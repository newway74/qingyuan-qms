package com.qms.framework.security;

import com.qms.common.constant.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌黑名单：登出后将 jti 写入 Redis，剩余有效期内不可再用。
 *
 * 高可用设计（单机版关键路径：每个请求都要查一次黑名单）：
 * Redis 不可用/超时时降级为"未拉黑"放行——JWT 本身仍有签名与过期校验，
 * 仅"登出后旧令牌立刻失效"这一能力临时减弱，待 Redis 恢复即自动复原。
 * 绝不能因为 Redis 抖动把全部请求线程堵在认证过滤器上导致"全站假死"。
 */
@Slf4j
@Service
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    /** 同类告警最小间隔（毫秒），防止故障期每个请求刷一条日志 */
    private static final long WARN_INTERVAL_MS = 30_000L;
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String jti, Instant expiresAt) {
        long ttl = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttl > 0) {
            try {
                redisTemplate.opsForValue().set(SecurityConstants.CACHE_TOKEN_BLACKLIST + jti, "1",
                        Duration.ofSeconds(ttl));
            } catch (DataAccessException e) {
                // 登出时 Redis 故障不应让用户看到 500：令牌自然过期即可兜底
                warnOnce("令牌黑名单写入失败（Redis 异常），登出在令牌过期前可能仍可短暂使用: {}",
                        e.getMessage());
            }
        }
    }

    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(SecurityConstants.CACHE_TOKEN_BLACKLIST + jti));
        } catch (DataAccessException e) {
            long now = System.currentTimeMillis();
            long last = lastWarnAt.get();
            if (now - last > WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
                log.warn("令牌黑名单查询失败（Redis 异常），本次降级放行，已签名且未过期的请求不受影响: {}",
                        e.getMessage());
            }
            return false;
        }
    }

    /** 受限流保护的 warn：避免故障期间日志风暴 */
    private void warnOnce(String pattern, String arg) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last > WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            log.warn(pattern, arg);
        }
    }
}
