package com.qms.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PING 版 Redis 健康检查：连通为 UP，异常为 DOWN。
 * 重点保证健康检查只依赖 PING，不会触发 INFO 输出的 Properties 解析
 * （即 Windows 路径中“反斜杠加 u”导致的转义解析问题）。
 */
class PingRedisHealthIndicatorTest {

    @Test
    void ping_ok_means_up() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenReturn("PONG");
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);

        Health health = new PingRedisHealthIndicator(factory).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("PONG", health.getDetails().get("ping"));
    }

    @Test
    void ping_error_means_down() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("connection refused"));

        Health health = new PingRedisHealthIndicator(factory).health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}
