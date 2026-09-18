package com.qms.modules.system.controller;

import com.qms.common.result.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 本机诊断端点：供单机版守护进程（launcher）做业务级探活与假死现场抓取。
 *
 * <p>背景：/actuator/health 与纯静态的 /api/v1/health 在 Tomcat 工作线程池或
 * Hikari 连接池被慢请求占满时，仍可能偶发返回正常，导致守护进程漏判“页面转圈”
 * 这类假死。本控制器的 /ping 走完整 MVC + 业务过滤器链，并真实执行
 * SELECT 1（Hikari 借连接，3s 语句超时）与 Redis PING（Lettuce 3s 命令超时），
 * 任一环节被卡住或失败即判定不可用。
 *
 * <p>/threaddump 仅在 qms.diag.enabled=true（dev/offline 本机 profile 开启）
 * 且请求来自回环地址时可用，供守护进程在判定假死的瞬间抓取 JVM 线程栈。
 */
@Slf4j
@Tag(name = "本机诊断（守护进程使用）")
@RestController
@RequestMapping("/api/v1/diag")
public class DiagController {

    /** 语句/命令级超时：探针必须快速失败，绝不能与被卡住的业务请求一起排队 */
    private static final int DB_QUERY_TIMEOUT_SECONDS = 3;

    private static final Set<String> LOOPBACK_ADDRS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${qms.diag.enabled:false}")
    private boolean diagEnabled;

    public DiagController(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        // 独立模板：强制 3 秒查询超时，不影响业务侧 JdbcTemplate 配置
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(DB_QUERY_TIMEOUT_SECONDS);
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * 业务探针：HTTP 始终 200，以 R.code 表达结果（与全站业务异常约定一致）。
     * code=0 且 db/redis 均 UP 才算存活；任何分项失败由守护进程计入假死判定。
     */
    @GetMapping("/ping")
    public R<Map<String, Object>> ping(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverTime", LocalDateTime.now().toString());
        body.put("loopback", isLoopback(request));

        boolean dbUp = probeDb(body);
        boolean redisUp = probeRedis(body);
        appendJvmInfo(body);

        if (dbUp && redisUp) {
            return R.ok(body);
        }
        return R.fail("DIAG_DOWN", "dbUp=" + dbUp + ", redisUp=" + redisUp);
    }

    /**
     * JVM 线程栈快照（jstack 文本格式）。仅本机 profile + 回环地址可调，
     * 生产 profile（qms.diag.enabled 缺省 false）直接拒绝。
     */
    @GetMapping(value = "/threaddump", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> threaddump(HttpServletRequest request) {
        if (!diagEnabled || !isLoopback(request)) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(renderThreadDump());
    }

    private boolean probeDb(Map<String, Object> body) {
        long start = System.currentTimeMillis();
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Map<String, Object> db = new LinkedHashMap<>();
            db.put("status", Integer.valueOf(1).equals(one) ? "UP" : "DOWN");
            db.put("latencyMs", System.currentTimeMillis() - start);
            body.put("db", db);
            return Integer.valueOf(1).equals(one);
        } catch (Exception ex) {
            log.warn("诊断探针 DB 检查失败：{}", ex.getMessage());
            body.put("db", probeError(start, ex));
            return false;
        }
    }

    private boolean probeRedis(Map<String, Object> body) {
        long start = System.currentTimeMillis();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            Map<String, Object> redis = new LinkedHashMap<>();
            redis.put("status", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
            redis.put("latencyMs", System.currentTimeMillis() - start);
            body.put("redis", redis);
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            log.warn("诊断探针 Redis 检查失败：{}", ex.getMessage());
            body.put("redis", probeError(start, ex));
            return false;
        }
    }

    private Map<String, Object> probeError(long start, Exception ex) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "DOWN");
        err.put("latencyMs", System.currentTimeMillis() - start);
        String msg = ex.getMessage();
        err.put("error", msg == null ? ex.getClass().getSimpleName() : msg.substring(0, Math.min(msg.length(), 200)));
        return err;
    }

    private void appendJvmInfo(Map<String, Object> body) {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        jvm.put("heapMaxMb", runtime.maxMemory() / 1024 / 1024);
        jvm.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        body.put("jvm", jvm);
    }

    private boolean isLoopback(HttpServletRequest request) {
        return LOOPBACK_ADDRS.contains(request.getRemoteAddr());
    }

    private String renderThreadDump() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append(LocalDateTime.now()).append("  qingyuan-qms diagnostic thread dump\n\n");

        long[] deadlocked = bean.findDeadlockedThreads();
        if (deadlocked != null && deadlocked.length > 0) {
            sb.append("!! DEADLOCKED THREADS: ").append(Arrays.toString(deadlocked)).append("\n\n");
        }

        ThreadInfo[] infos = bean.dumpAllThreads(true, true);
        for (ThreadInfo info : infos) {
            sb.append('"').append(info.getThreadName()).append('"')
                    .append(" Id=").append(info.getThreadId())
                    .append(' ').append(info.getThreadState());
            if (info.getLockName() != null) {
                sb.append(" on ").append(info.getLockName());
            }
            if (info.getLockOwnerName() != null) {
                sb.append(" owned by \"").append(info.getLockOwnerName())
                        .append("\" Id=").append(info.getLockOwnerId());
            }
            sb.append('\n');
            if (info.isSuspended()) {
                sb.append("   (suspended)\n");
            }
            if (info.isInNative()) {
                sb.append("   (in native)\n");
            }
            int waited = (int) Math.min(info.getLockedMonitors().length, 8);
            StackTraceElement[] stack = info.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                sb.append("\tat ").append(stack[i]).append('\n');
                if (i == 0 && info.getLockInfo() != null) {
                    sb.append("\t-  waiting on ").append(info.getLockInfo()).append('\n');
                }
                for (int mi = 0; mi < waited; mi++) {
                    if (info.getLockedMonitors()[mi].getLockedStackDepth() == i) {
                        sb.append("\t-  locked ").append(info.getLockedMonitors()[mi]).append('\n');
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
