package com.qms.framework.biz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务单号生成器：Redis INCR + 首次播种对齐库内当日最大值、并发锁未抢到的等待路径、
 * 未知前缀/库异常降级、单号格式与 4 位序号。
 */
@ExtendWith(MockitoExtension.class)
class BizNoGeneratorTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private BizNoGenerator generator;
    private String day;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        generator = new BizNoGenerator(redisTemplate, jdbcTemplate);
        day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @Test
    void first_sequence_seeds_from_db_max_then_increments() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        AtomicInteger incr = new AtomicInteger();
        when(valueOps.increment(anyString())).thenAnswer(inv -> {
            int n = incr.incrementAndGet();
            return n == 1 ? 1L : 6L;
        });
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(5);

        String no = generator.next("CY");

        assertEquals("CY" + day + "0006", no);
        // 播种把序列对齐为库内最大值 5
        verify(valueOps).set(anyString(), eq("5"));
        verify(redisTemplate).expire(anyString(), any(Duration.class));
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void first_sequence_with_empty_db_starts_from_one() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(null);

        assertEquals("CY" + day + "0001", generator.next("CY"));
    }

    @Test
    void non_first_increment_returns_redis_seq_directly() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(42L);

        assertEquals("JC" + day + "0042", generator.next("JC"));
    }

    @Test
    void lock_not_acquired_waits_seed_then_increments() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
        when(valueOps.get(anyString())).thenReturn("3");
        when(valueOps.increment(anyString())).thenReturn(4L);

        assertEquals("YP" + day + "0004", generator.next("YP"));
        // 等锁路径不应查库播种，也不删除别人的锁
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void unknown_prefix_skips_db_seed() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        String no = generator.next("XX");
        assertEquals("XX" + day + "0001", no);
    }

    @Test
    void db_query_exception_degrades_to_zero_and_still_returns_no() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenThrow(new RuntimeException("table not exist"));

        String no = generator.next("BH");
        assertTrue(no.startsWith("BH" + day));
    }
}
