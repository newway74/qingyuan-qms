package com.qms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 主链路集成测试基类：真实 Spring 容器 + MockMvc（含安全过滤链），
 * 落本机独立库 qms_test 与 Redis db15，每轮以唯一批次号造数，可重复执行。
 * 有 Docker 时可用 -DTESTCONTAINERS=true 切换容器化中间件（见 README）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

    protected static final String API = "/api/v1";
    /** V13 轮换后的统一演示密码（admin 及四个业务角色均为该密码） */
    protected static final String PWD = "Qms@Demo2026";
    protected static final ObjectMapper M = new ObjectMapper();

    /** 1x1 PNG */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @org.junit.jupiter.api.BeforeEach
    void flushIsolationRedis() {
        flushRedis();
    }

    @BeforeAll
    static void note() {
        // 仅作日志锚点，便于在 failsafe 报告中识别
        System.out.println("[IT] running against local MariaDB qms_test + Redis db15");
    }

    /**
     * 可选 Testcontainers 通道：-DTESTCONTAINERS=true（或环境变量）时，
     * IT 自动使用一次性 MySQL8/Redis7 容器；默认走本机 MariaDB+Redis（qms_test / db15）。
     */
    @DynamicPropertySource
    static void optionalContainers(DynamicPropertyRegistry registry) {
        boolean enabled = Boolean.getBoolean("TESTCONTAINERS")
                || "true".equalsIgnoreCase(System.getenv().getOrDefault("TESTCONTAINERS", "false"));
        if (!enabled) {
            return;
        }
        System.out.println("[IT] TESTCONTAINERS=true -> starting MySQL 8.0 / Redis 7 containers");
        MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("qms_test")
                .withUsername("root")
                .withPassword("root123")
                .withUrlParam("serverTimezone", "Asia/Shanghai");
        mysql.start();
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    protected void flushRedis() {
        redisConnectionFactory.getConnection().serverCommands().flushDb();
    }

    // ---------------------------------------------------------------- HTTP 辅助

    protected String login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post(API + "/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = read(res);
        assertEquals("0", body.path("code").asText(), "login failed: " + res.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode read(MvcResult res) throws Exception {
        return M.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 业务调用：返回统一返回体（业务错误也是 HTTP 200） */
    protected JsonNode postJson(String path, String token, Object body) throws Exception {
        MvcResult res = mockMvc.perform(post(API + path)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(body == null ? "{}" : M.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return read(res);
    }

    protected JsonNode putJson(String path, String token, Object body) throws Exception {
        MvcResult res = mockMvc.perform(put(API + path)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(M.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return read(res);
    }

    protected JsonNode getJson(String path, String token) throws Exception {
        MvcResult res = mockMvc.perform(get(API + path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return read(res);
    }

    /** 断言业务成功并返回 data 节点 */
    protected JsonNode ok(JsonNode resp, String hint) {
        assertEquals("0", resp.path("code").asText(),
                () -> hint + " expected code=0 but got " + resp.toPrettyString());
        return resp.path("data");
    }

    protected int httpStatusPost(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(API + path)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(body == null ? "{}" : M.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
    }

    protected JsonNode expectBizCode(String path, String token, Object body, String... codes) throws Exception {
        JsonNode resp = postJson(path, token, body);
        boolean any = false;
        for (String c : codes) {
            any |= c.equals(resp.path("code").asText());
        }
        assertTrue(any, () -> "expected one of " + String.join(",", codes) + " but got " + resp);
        return resp;
    }

    protected long uploadEvidence(String token, long bizId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.png", "image/png", PNG);
        MvcResult res = mockMvc.perform(multipart(API + "/attachments/upload")
                        .file(file)
                        .header("Authorization", bearer(token))
                        .param("bizType", "qc_inspection_result")
                        .param("bizId", String.valueOf(bizId)))
                .andExpect(status().isOk())
                .andReturn();
        return read(res).path("data").path("id").asLong();
    }

    // ---------------------------------------------------------------- 业务流程辅助

    protected record ChainTokens(String admin, String sampler, String qa, String inspector, String reviewer,
                                 long inspectorId, long reviewerId) {
    }

    protected ChainTokens allTokens() throws Exception {
        String admin = login("admin", PWD);
        String sampler = login("sampler", PWD);
        String qa = login("qamanager", PWD);
        String inspector = login("inspector", PWD);
        String reviewer = login("reviewer", PWD);
        JsonNode inspectors = getJson("/inspection/tasks/users?roleCode=INSPECTOR", qa);
        JsonNode reviewers = getJson("/inspection/tasks/users?roleCode=REVIEWER", qa);
        return new ChainTokens(admin, sampler, qa, inspector, reviewer,
                findUserId(inspectors, "inspector"), findUserId(reviewers, "reviewer"));
    }

    private long findUserId(JsonNode users, String username) {
        for (JsonNode u : users.path("data")) {
            if (username.equals(u.path("username").asText())) {
                return u.path("id").asLong();
            }
        }
        throw new IllegalStateException("user not found: " + username);
    }

    /** 抽样→提交→收样→分配→开始检验，返回 taskId */
    protected long newTask(ChainTokens t, long skuId, String batchNo, String source) throws Exception {
        String today = LocalDate.now().toString();
        String expiry = LocalDate.now().plusDays(365).toString();
        Map<String, Object> sampling = new HashMap<>();
        sampling.put("source", source);
        sampling.put("skuId", skuId);
        sampling.put("batchNo", batchNo);
        sampling.put("productionDate", today);
        sampling.put("expiryDate", expiry);
        sampling.put("sampleQuantity", 250);
        sampling.put("quantityUnit", "g");
        sampling.put("samplingLocation", "WH-IT");
        long samplingId = ok(postJson("/sampling", t.sampler(), sampling), "create sampling").asLong();
        ok(postJson("/sampling/" + samplingId + "/submit", t.sampler(), null), "submit sampling");

        Map<String, Object> receive = new HashMap<>();
        receive.put("samplingId", samplingId);
        receive.put("packageBatchCheck", "MATCH");
        receive.put("productionDate", today);
        receive.put("expiryDate", expiry);
        receive.put("inspectionCount", 1);
        receive.put("retainFlag", false);
        receive.put("backupFlag", false);
        JsonNode recv = ok(postJson("/samples/receive", t.sampler(), receive), "receive sample");
        long taskId = recv.path("samples").get(0).path("taskId").asLong();

        Map<String, Object> assign = new HashMap<>();
        assign.put("taskId", taskId);
        assign.put("inspectorId", t.inspectorId());
        assign.put("reviewerId", t.reviewerId());
        ok(postJson("/inspection/tasks/assign", t.qa(), assign), "assign task");
        ok(postJson("/inspection/tasks/" + taskId + "/start", t.inspector(), null), "start task");
        return taskId;
    }

    /**
     * 按模板逐项填值并提交。
     * mode：allPass 全合格；oneAFail 首个 A 类一票否决定性项判不合格；allNonAFail 全部非 A 定性项判不合格。
     * 返回不合格项数。
     */
    protected int fillAndSubmit(ChainTokens t, long taskId, String mode) throws Exception {
        JsonNode pre = ok(getJson("/inspection/tasks/" + taskId, t.inspector()), "preload task");
        if ("PENDING_INSPECT".equals(pre.path("task").path("status").asText())) {
            ok(postJson("/inspection/tasks/" + taskId + "/start", t.inspector(), null), "start pending task");
        }
        JsonNode detail = ok(getJson("/inspection/tasks/" + taskId, t.inspector()), "load task");
        JsonNode items = detail.path("items");
        JsonNode results = detail.path("results");
        Map<Long, JsonNode> itemById = new HashMap<>();
        for (JsonNode item : items) {
            itemById.put(item.path("id").asLong(), item);
        }
        ArrayNode payload = M.createArrayNode();
        int failCount = 0;
        for (JsonNode r : results) {
            long resultId = r.path("id").asLong();
            long itemId = r.path("itemId").asLong();
            JsonNode item = itemById.get(itemId);
            ObjectNode row = payload.addObject();
            row.put("resultId", resultId);
            row.put("itemId", itemId);
            String resultType = item.path("resultType").asText();
            if ("QUALITATIVE".equals(resultType)) {
                String qv = "PASS";
                String level = item.path("defectLevel").asText();
                int veto = item.path("vetoFlag").asInt(0);
                if ("allNonAFail".equals(mode) && !"A".equals(level)) {
                    qv = "FAIL";
                    failCount++;
                } else if ("oneAFail".equals(mode) && failCount == 0 && "A".equals(level) && veto == 1) {
                    qv = "FAIL";
                    failCount++;
                }
                row.put("qualitativeValue", qv);
            } else if ("QUANTITATIVE".equals(resultType)) {
                if ("JJF1070".equals(item.path("toleranceRule").asText())) {
                    row.put("quantitativeValue", 250);
                } else if (!item.path("maxValue").isMissingNode() && !item.path("maxValue").isNull()
                        && !item.path("minValue").isMissingNode() && !item.path("minValue").isNull()) {
                    row.put("quantitativeValue", item.path("maxValue").asDouble()
                            + (item.path("minValue").asDouble() - item.path("maxValue").asDouble()) / 2);
                } else if (!item.path("maxValue").isMissingNode() && !item.path("maxValue").isNull()) {
                    row.put("quantitativeValue", item.path("maxValue").asDouble() * 0.5);
                } else if (!item.path("minValue").isMissingNode() && !item.path("minValue").isNull()) {
                    row.put("quantitativeValue", item.path("minValue").asDouble());
                } else {
                    row.put("quantitativeValue", 1);
                }
            } else if ("DOCUMENT".equals(resultType)) {
                row.put("docAttachmentId", uploadEvidence(t.inspector(), resultId));
            }
        }
        ok(postJson("/inspection/tasks/" + taskId + "/results", t.inspector(),
                Map.of("results", payload)), "save results");
        ok(postJson("/inspection/tasks/" + taskId + "/submit", t.inspector(),
                Map.of("password", PWD)), "submit inspection");
        return failCount;
    }

    protected JsonNode reviewTask(ChainTokens t, long taskId, String action, String conclusion) throws Exception {
        return ok(postJson("/inspection/tasks/" + taskId + "/review", t.reviewer(),
                Map.of("action", action, "password", PWD, "conclusion", conclusion)), "review task");
    }

    protected JsonNode findCaseByReport(ChainTokens t, long reportId) throws Exception {
        JsonNode page = ok(getJson("/defect/cases?pageSize=100", t.qa()), "list cases");
        for (JsonNode rec : page.path("records")) {
            if (rec.path("caseObj").path("reportId").asLong() == reportId) {
                return rec.path("caseObj");
            }
        }
        return null;
    }
}
