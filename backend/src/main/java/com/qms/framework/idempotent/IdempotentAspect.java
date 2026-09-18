package com.qms.framework.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.constant.SecurityConstants;
import com.qms.common.exception.BizException;
import com.qms.common.result.R;
import com.qms.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 幂等切面：基于 Redis SET NX EX。
 * 未携带 Idempotency-Key 的请求不拦截（注解表示该接口支持而非强制）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PROCESSING = "PROCESSING";

    /** 同类告警最小间隔（毫秒），Redis 故障期避免日志风暴 */
    private static final long WARN_INTERVAL_MS = 30_000L;
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        String key = request == null ? null : request.getHeader(SecurityConstants.IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            return pjp.proceed();
        }
        String redisKey = SecurityConstants.CACHE_IDEMPOTENT + key;
        Boolean first;
        try {
            first = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, PROCESSING, Duration.ofSeconds(idempotent.ttl()));
        } catch (DataAccessException e) {
            // Redis 故障时降级：直接执行业务。单机版并发极低，偶发重复提交风险可接受，
            // 数据库业务校验/唯一索引仍在；绝不能让缓存故障导致全部写操作 500
            warnDegraded(e.getMessage());
            return pjp.proceed();
        }
        if (Boolean.FALSE.equals(first)) {
            String cached;
            try {
                cached = redisTemplate.opsForValue().get(redisKey);
            } catch (DataAccessException e) {
                warnDegraded(e.getMessage());
                return pjp.proceed();
            }
            if (PROCESSING.equals(cached)) {
                throw new BizException(ResultCode.REPEAT_SUBMIT, "请求正在处理中，请勿重复提交");
            }
            if (cached != null) {
                return objectMapper.readValue(cached, R.class);
            }
            throw new BizException(ResultCode.REPEAT_SUBMIT);
        }
        try {
            Object result = pjp.proceed();
            if (result instanceof R<?> r) {
                try {
                    redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(r),
                            Duration.ofSeconds(idempotent.ttl()));
                } catch (DataAccessException e) {
                    warnDegraded(e.getMessage());
                }
            }
            return result;
        } catch (Throwable ex) {
            // 业务异常不占用幂等键，允许修正后重试
            try {
                redisTemplate.delete(redisKey);
            } catch (DataAccessException e) {
                warnDegraded(e.getMessage());
            }
            throw ex;
        }
    }

    /** Redis 故障降级告警（限流，30 秒最多一条） */
    private void warnDegraded(String reason) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last > WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("幂等控制依赖 Redis 异常，本次写操作降级为直接执行（重复提交防护临时失效）: {}", reason);
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
