package com.qms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查（PING 实现），用于覆盖 Spring Boot 默认的 RedisHealthIndicator。
 *
 * <p>为什么不用默认实现：默认健康检查执行 Redis 的 {@code INFO} 命令，并使用
 * {@link java.util.Properties#load} 解析返回内容。Windows 单机版解压在
 * C:/Users 等目录时（路径中出现“反斜杠紧跟字母 u”的片段），INFO 输出中的
 * executable / config_file 字段携带该路径，该片段会被 Properties.loadConvert
 * 当作 Unicode 转义，因为后面不是 4 位十六进制数字而抛出
 * {@code IllegalArgumentException: Malformed encoding}（JDK Properties
 * 的反斜杠加 u 转义解析错误），导致
 * /actuator/health 中 redis 分项恒为 DOWN（启动器据此误判后端启动失败）。
 *
 * <p>Redis 命令通道本身完全正常（业务读写不经过 INFO 解析），健康检查只需
 * PING 一次确认可连通即可。Bean 名固定为 redisHealthIndicator，与 Spring Boot
 * 同步/响应式两个 Redis 健康自动配置的 @ConditionalOnMissingBean 条件一致，
 * 可同时替换两者，健康聚合中的分项名仍为 "redis"。
 */
@Slf4j
@Component("redisHealthIndicator")
public class PingRedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public PingRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return Health.up().withDetail("ping", pong).build();
        } catch (Exception ex) {
            log.warn("Redis PING 健康检查失败：{}", ex.getMessage());
            return Health.down(ex).build();
        }
    }
}
