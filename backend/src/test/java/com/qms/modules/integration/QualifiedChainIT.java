package com.qms.modules.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.qms.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合格主链路 + 角色越权：抽样→收样→分配→检验录入(全合格)→复核签署→报告双签合格→签发。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QualifiedChainIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("合格链路：全项合格 → QUALIFIED 双签报告 → 可签发")
    void qualified_chain_end_to_end() throws Exception {
        ChainTokens t = allTokens();
        String tag = "ITQ" + System.currentTimeMillis();
        long taskId = newTask(t, 3006, tag, "INCOMING_BATCH");

        int fails = fillAndSubmit(t, taskId, "allPass");
        assertEquals(0, fails);

        reviewTask(t, taskId, "PASS", "QUALIFIED");

        JsonNode detail = ok(getJson("/inspection/tasks/" + taskId, t.qa()), "task after review");
        JsonNode task = detail.path("task");
        JsonNode report = detail.path("report");
        assertTrue(report.has("id"), "report must exist after dual sign");
        assertEquals("SIGNED", report.path("status").asText());
        assertEquals("QUALIFIED", report.path("conclusion").asText());
        assertEquals("JUDGED", task.path("status").asText(), "复核通过后任务进入 JUDGED");

        // 签发报告
        long reportId = report.path("id").asLong();
        ok(postJson("/inspection/reports/" + reportId + "/issue", t.reviewer(), null), "issue report");
        JsonNode issued = ok(getJson("/inspection/reports/" + reportId, t.qa()), "load report");
        assertEquals("ISSUED", issued.path("report").path("status").asText());

        // 合格链路不应产生不合格单
        assertEquals(null, findCaseByReport(t, reportId));
    }

    @Test
    @DisplayName("越权：收样员不能分配任务；检验员不能复核；未登录 401")
    void rbac_guards_on_main_chain() throws Exception {
        ChainTokens t = allTokens();
        String tag = "ITG" + System.currentTimeMillis();
        long taskId = newTask(t, 3006, tag, "PERIODIC");
        fillAndSubmit(t, taskId, "allPass");

        // 收样员分配任务 → 403
        int samplerAssign = httpStatusPost("/inspection/tasks/assign", t.sampler(),
                Map.of("taskId", taskId, "inspectorId", t.inspectorId(), "reviewerId", t.reviewerId()));
        assertEquals(403, samplerAssign);

        // 检验员执行复核 → 403
        int inspectorReview = httpStatusPost("/inspection/tasks/" + taskId + "/review", t.inspector(),
                Map.of("action", "PASS", "password", PWD, "conclusion", "QUALIFIED"));
        assertEquals(403, inspectorReview);

        // 无令牌 → 401（安全链返回 401，而非 200）
        int anonymous = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get(API + "/inspection/tasks/" + taskId))
                .andReturn().getResponse().getStatus();
        assertEquals(401, anonymous);
    }

    @Test
    @DisplayName("状态机：未开始检验直接提交结果被业务拒绝")
    void illegal_transition_rejected() throws Exception {
        ChainTokens t = allTokens();
        String tag = "ITX" + System.currentTimeMillis();
        long taskId = newTask(t, 3006, tag, "INCOMING_BATCH");

        // 直接复核一个尚未录入提交的任务
        JsonNode resp = postJson("/inspection/tasks/" + taskId + "/review", t.reviewer(),
                Map.of("action", "PASS", "password", PWD, "conclusion", "QUALIFIED"));
        assertNotNull(resp.path("code").asText(null));
        assertTrue(!"0".equals(resp.path("code").asText()),
                "review before submit must be rejected: " + resp);
    }
}
