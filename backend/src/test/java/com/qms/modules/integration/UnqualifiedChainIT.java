package com.qms.modules.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.qms.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不合格闭环主链路：A 类一票否决 → 自动立案（禁止让步）→ 评审整改 → 审批驳回再通过
 * → 整改单下发/回复/验证（先 FAIL 后 PASS）→ 复检子任务全合格双签 → 不合格单与整改单闭环
 * → 父子任务关闭，原报告内容不可变。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnqualifiedChainIT extends AbstractIntegrationTest {

    private ChainTokens tokens;
    private long taskId;
    private long reportId;
    private long caseId;
    private long rectId;
    private long childId;
    private String originalReportJson;

    @Test
    @DisplayName("步骤1：A 类否决项不合格 → 复核 UNQUALIFIED → 双签报告 → 签发 → 自动立案")
    @org.junit.jupiter.api.Order(1)
    void unqualified_review_auto_case() throws Exception {
        tokens = allTokens();
        String tag = "ITR" + System.currentTimeMillis();
        taskId = newTask(tokens, 3002, tag, "INCOMING_BATCH");

        int fails = fillAndSubmit(tokens, taskId, "oneAFail");
        assertTrue(fails >= 1, "必须选中至少 1 个 A 类一票否决项");

        reviewTask(tokens, taskId, "PASS", "UNQUALIFIED");

        JsonNode detail = ok(getJson("/inspection/tasks/" + taskId, tokens.qa()), "task reviewed");
        JsonNode report = detail.path("report");
        assertEquals("SIGNED", report.path("status").asText());
        assertEquals("UNQUALIFIED", report.path("conclusion").asText());
        reportId = report.path("id").asLong();

        ok(postJson("/inspection/reports/" + reportId + "/issue", tokens.reviewer(), null), "issue report");

        JsonNode caseObj = findCaseByReport(tokens, reportId);
        assertNotNull(caseObj, "不合格报告签发后必须自动生成不合格单");
        assertEquals("PENDING_REVIEW", caseObj.path("status").asText());
        assertEquals("A", caseObj.path("maxSeverity").asText());
        assertTrue(caseObj.path("caseNo").asText().startsWith("BH"));
        caseId = caseObj.path("id").asLong();

        // 幂等：同一报告只有一张不合格单
        JsonNode page = ok(getJson("/defect/cases?pageSize=100", tokens.qa()), "cases page");
        int same = 0;
        for (JsonNode rec : page.path("records")) {
            if (rec.path("caseObj").path("reportId").asLong() == reportId) {
                same++;
            }
        }
        assertEquals(1, same);
    }

    @Test
    @DisplayName("步骤2：A 类禁止让步；未评审不能审批；检验员无审批权(403)")
    @org.junit.jupiter.api.Order(2)
    void concession_blocked_and_rbac() throws Exception {
        expectBizCode("/defect/cases/" + caseId + "/review", tokens.reviewer(),
                Map.of("disposition", "CONCESSION", "rootCause", "try concession for A"),
                "BIZ_JUDGE_VETO_FAIL");

        JsonNode earlyApprove = postJson("/defect/cases/" + caseId + "/approve", tokens.qa(),
                Map.of("action", "PASS", "password", PWD));
        assertNotEquals("0", earlyApprove.path("code").asText());

        assertEquals(403, httpStatusPost("/defect/cases/" + caseId + "/approve", tokens.inspector(),
                Map.of("action", "PASS", "password", PWD)));
    }

    @Test
    @DisplayName("步骤3：评审整改 → 审批先驳回退回评审 → 再评审 → 密码确认通过 → PROCESSING")
    @org.junit.jupiter.api.Order(3)
    void review_rectify_approve_reject_then_pass() throws Exception {
        ok(postJson("/defect/cases/" + caseId + "/review", tokens.reviewer(),
                Map.of("disposition", "RECTIFY", "rootCause", "process parameter drift",
                        "comment", "it review")), "review rectify");
        assertEquals("PENDING_APPROVAL",
                ok(getJson("/defect/cases/" + caseId, tokens.qa()), "case pending approval")
                        .path("caseObj").path("status").asText());

        expectBizCode("/defect/cases/" + caseId + "/approve", tokens.qa(),
                Map.of("action", "PASS", "password", "WRONG-PWD"),
                "AUTH_PASSWORD_CONFIRM_FAIL");

        ok(postJson("/defect/cases/" + caseId + "/approve", tokens.qa(),
                Map.of("action", "REJECT", "comment", "please supplement")), "approve reject");
        assertEquals("PENDING_REVIEW",
                ok(getJson("/defect/cases/" + caseId, tokens.qa()), "back to review")
                        .path("caseObj").path("status").asText());

        ok(postJson("/defect/cases/" + caseId + "/review", tokens.reviewer(),
                Map.of("disposition", "RECTIFY", "rootCause", "process parameter drift 2nd")),
                "review rectify again");
        ok(postJson("/defect/cases/" + caseId + "/approve", tokens.qa(),
                Map.of("action", "PASS", "password", PWD, "comment", "approved")), "approve pass");
        assertEquals("PROCESSING",
                ok(getJson("/defect/cases/" + caseId, tokens.qa()), "case processing")
                        .path("caseObj").path("status").asText());
    }

    @Test
    @DisplayName("步骤4：整改单下发（重复创建拦截）→ 未回复不能验证 → 回复 → 验证先 FAIL 后 PASS 生成复检任务")
    @org.junit.jupiter.api.Order(4)
    void rectification_lifecycle_and_recheck_task() throws Exception {
        String nearExp = LocalDate.now().plusDays(10).toString();
        JsonNode created = ok(postJson("/rectifications", tokens.qa(),
                Map.of("defectCaseId", caseId,
                        "issueDesc", "A-level nonconformity, corrective action required",
                        "planFinishDate", nearExp)), "create rectification");
        rectId = created.asLong();
        assertTrue(rectId > 0);

        expectBizCode("/rectifications", tokens.qa(),
                Map.of("defectCaseId", caseId, "issueDesc", "dup"), "DATA_DUPLICATED");

        JsonNode rect0 = ok(getJson("/rectifications/" + rectId, tokens.qa()), "rect issued");
        assertEquals("ISSUED", rect0.path("rect").path("status").asText());
        assertTrue(rect0.path("rect").path("rectifyNo").asText().startsWith("ZG"));

        // 复核人不能验证整改（仅质量主管）
        assertEquals(403, httpStatusPost("/rectifications/" + rectId + "/verify", tokens.reviewer(),
                Map.of("result", "PASS", "password", PWD)));
        // 未回复先验证 → 业务拒绝
        assertNotEquals("0", postJson("/rectifications/" + rectId + "/verify", tokens.qa(),
                Map.of("result", "PASS", "password", PWD)).path("code").asText());

        String today = LocalDate.now().toString();
        ok(putJson("/rectifications/" + rectId, tokens.qa(),
                Map.of("correctiveAction", "recalibrate equipment and update SOP",
                        "planFinishDate", nearExp, "actualFinishDate", today)), "rect reply");

        ok(postJson("/rectifications/" + rectId + "/verify", tokens.qa(),
                Map.of("result", "FAIL", "password", PWD, "comment", "still failing")), "verify fail");
        assertEquals("FAILED",
                ok(getJson("/rectifications/" + rectId, tokens.qa()), "rect failed")
                        .path("rect").path("status").asText());

        ok(putJson("/rectifications/" + rectId, tokens.qa(),
                Map.of("correctiveAction", "2nd rectification: replace mold")), "reply again");
        ok(postJson("/rectifications/" + rectId + "/verify", tokens.qa(),
                Map.of("result", "PASS", "password", PWD, "comment", "verified")), "verify pass");

        JsonNode verifying = ok(getJson("/rectifications/" + rectId, tokens.qa()), "rect verifying");
        assertEquals("VERIFYING", verifying.path("rect").path("status").asText());
        childId = verifying.path("rect").path("recheckTaskId").asLong();
        assertTrue(childId > 0, "验证通过必须自动生成复检任务");
        assertTrue(verifying.path("recheckTaskNo").asText().startsWith("JC"));

        JsonNode parent = ok(getJson("/inspection/tasks/" + taskId, tokens.qa()), "parent rechecking");
        assertEquals("RECHECKING", parent.path("task").path("status").asText());
        JsonNode child = ok(getJson("/inspection/tasks/" + childId, tokens.qa()), "child round2");
        assertEquals(2, child.path("task").path("roundNo").asInt());
        assertEquals(String.valueOf(taskId), child.path("task").path("recheckParentId").asText());
    }

    @Test
    @DisplayName("步骤5：复检全合格双签 QUALIFIED → 不合格单 CLOSED + 整改 PASSED；原报告不可变；父子关闭")
    @org.junit.jupiter.api.Order(5)
    void recheck_pass_closes_case_and_keeps_original_report() throws Exception {
        originalReportJson = ok(getJson("/inspection/reports/" + reportId, tokens.qa()), "orig report")
                .path("report").toString();

        int childFails = fillAndSubmit(tokens, childId, "allPass");
        assertEquals(0, childFails);
        reviewTask(tokens, childId, "PASS", "QUALIFIED");

        JsonNode closedCase = ok(getJson("/defect/cases/" + caseId, tokens.qa()), "case closed by event");
        assertEquals("CLOSED", closedCase.path("caseObj").path("status").asText());
        assertNotNull(closedCase.path("caseObj").path("closedAt").asText(null));

        JsonNode rectPassed = ok(getJson("/rectifications/" + rectId, tokens.qa()), "rect passed");
        assertEquals("PASSED", rectPassed.path("rect").path("status").asText());

        JsonNode timeline = ok(getJson("/defect/cases/" + caseId + "/timeline", tokens.qa()), "timeline");
        StringBuilder nodes = new StringBuilder();
        for (JsonNode n : timeline) {
            nodes.append(n.path("node").asText()).append(',');
        }
        String joined = nodes.toString();
        assertTrue(joined.contains("RECTIFY"));
        assertTrue(joined.contains("RECHECK"));
        assertTrue(timeline.size() >= 6, "timeline nodes >= 6, actual=" + timeline.size());

        // 原报告内容必须与复检前完全一致（原任务原报告不可变）
        String nowReportJson = ok(getJson("/inspection/reports/" + reportId, tokens.qa()), "report after close")
                .path("report").toString();
        assertEquals(originalReportJson, nowReportJson, "原检验报告不得被复检改写");

        ok(postJson("/inspection/tasks/" + childId + "/close", tokens.qa(), null), "close child");
        ok(postJson("/inspection/tasks/" + taskId + "/close", tokens.qa(), null), "close parent");
        assertEquals("CLOSED", ok(getJson("/inspection/tasks/" + childId, tokens.qa()), "child closed")
                .path("task").path("status").asText());
        assertEquals("CLOSED", ok(getJson("/inspection/tasks/" + taskId, tokens.qa()), "parent closed")
                .path("task").path("status").asText());

        // 已关闭任务不能再发起复检
        assertNotEquals("0", postJson("/inspection/tasks/" + childId + "/recheck", tokens.qa(),
                Map.of("reason", "again")).path("code").asText());
    }
}
