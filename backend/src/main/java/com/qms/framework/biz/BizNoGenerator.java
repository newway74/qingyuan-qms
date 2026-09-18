package com.qms.framework.biz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单据编号生成器：前缀 + yyyyMMdd + 4位日序列，如 CY202609150001。
 * 序列基于 Redis INCR 原子自增；为防止 Redis 重启/清库导致序列归零与历史单号冲突，
 * 首次自增（key 不存在）时以数据库当日最大序号播种；数据库唯一索引为最终兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BizNoGenerator {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    /** 前缀 → (表名, 单号列名)。阶段4后的表缺省时跳过播种，仅靠 Redis/唯一索引。 */
    private static final Map<String, String[]> TABLES = Map.of(
            "CY", new String[]{"qc_sampling", "sampling_no"},
            "YP", new String[]{"qc_sample", "sample_no"},
            "JC", new String[]{"qc_inspection_task", "task_no"},
            "BG", new String[]{"qc_inspection_report", "report_no"},
            "BH", new String[]{"qc_defect_case", "case_no"},
            "ZG", new String[]{"qc_supplier_rectification", "rectify_no"},
            "NP", new String[]{"qc_npi_project", "project_no"},
            "PG", new String[]{"qc_npi_eval", "eval_no"},
            "YC", new String[]{"qc_factory_audit", "audit_no"},
            "WJ", new String[]{"qc_external_test", "test_no"}
    );

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Redis 故障降级发号器：key=前缀:日期，值为"库内当日最大值与内存值"取高后的递增序列。
     * 单机版只有一个应用实例，进程内 synchronized 即可保证不重号；
     * 与库内已有最大值对齐，再叠加唯一索引最终兜底。
     */
    private final Map<String, AtomicLong> fallbackSeq = new ConcurrentHashMap<>();
    /** 降级发号锁（按前缀+日期串行取号） */
    private final Map<String, Object> fallbackLocks = new ConcurrentHashMap<>();

    public String next(String prefix) {
        String day = LocalDate.now().format(DAY_FMT);
        long seq;
        try {
            seq = nextByRedis(prefix, day);
        } catch (DataAccessException e) {
            // Redis 不可用（故障/重启中）时降级为数据库+内存发号，保证业务写操作不中断
            log.warn("业务单号生成依赖 Redis 异常，降级为数据库发号 prefix={} day={}: {}",
                    prefix, day, e.getMessage());
            seq = nextByDatabase(prefix, day);
        }
        return prefix + day + String.format("%04d", seq);
    }

    private long nextByRedis(String prefix, String day) {
        String seqKey = "qms:seq:" + prefix + ":" + day;
        long seq;
        // 并发下仅允许一个线程执行“丢失播种”，其余线程等待后直接 INCR
        String lockKey = "qms:seq:lock:" + prefix + ":" + day;
        boolean locked = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5)));
        try {
            if (locked) {
                Long first = redisTemplate.opsForValue().increment(seqKey);
                redisTemplate.expire(seqKey, Duration.ofDays(2));
                if (first != null && first == 1L) {
                    // key 刚建立：可能是新一天，也可能是 Redis 丢失后归零——与库内当日最大值对齐
                    long dbMax = queryDbMaxSeq(prefix, day);
                    if (dbMax > 0) {
                        redisTemplate.opsForValue().set(seqKey, String.valueOf(dbMax));
                        seq = redisTemplate.opsForValue().increment(seqKey);
                    } else {
                        seq = first;
                    }
                } else {
                    seq = first;
                }
            } else {
                if (!waitSeed(seqKey)) {
                    log.warn("业务单号播种等待超时 prefix={} day={}，直接自增（依赖唯一索引兜底）", prefix, day);
                }
                seq = redisTemplate.opsForValue().increment(seqKey);
            }
        } finally {
            if (locked) {
                try {
                    redisTemplate.delete(lockKey);
                } catch (DataAccessException ignored) {
                    // 锁释放失败不影响主流程，5 秒后自动过期
                }
            }
        }
        return seq;
    }

    /**
     * 降级发号：库内当日最大值与内存序列取高后 +1。
     * 每次都查一次 MAX（带主键/索引，毫秒级），即使应用重启也不会回退到已用序号。
     */
    private long nextByDatabase(String prefix, String day) {
        String key = prefix + ":" + day;
        Object lock = fallbackLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            long dbMax = queryDbMaxSeq(prefix, day);
            long current = fallbackSeq.computeIfAbsent(key, k -> new AtomicLong(0L)).get();
            long next = Math.max(dbMax, current) + 1;
            fallbackSeq.get(key).set(next);
            return next;
        }
    }

    /** 等待播种完成：序号 key 存在且值 > 0 即认为可用（最多约 5 秒）。 */
    private boolean waitSeed(String seqKey) {
        for (int i = 0; i < 25; i++) {
            String v = redisTemplate.opsForValue().get(seqKey);
            if (v != null) {
                try {
                    if (Long.parseLong(v) > 0) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** 查库内当日最大序号（单号 = 前缀 + 8位日期 + 4位序列）。表不存在等异常降级为 0。 */
    private long queryDbMaxSeq(String prefix, String day) {
        String[] table = TABLES.get(prefix);
        if (table == null) {
            return 0L;
        }
        try {
            int seqStart = prefix.length() + 8 + 1;
            String sql = "SELECT MAX(CAST(SUBSTRING(" + table[1] + ", " + seqStart + ") AS UNSIGNED)) "
                    + "FROM " + table[0] + " WHERE " + table[1] + " LIKE ?";
            Integer max = jdbcTemplate.queryForObject(sql, Integer.class, prefix + day + "%");
            return max == null ? 0L : max;
        } catch (Exception e) {
            log.warn("查询业务单号当日最大值失败 prefix={} table={}，降级为0: {}",
                    prefix, table[0], e.getMessage());
            return 0L;
        }
    }
}
