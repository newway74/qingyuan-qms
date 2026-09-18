package com.qms.framework.statemachine;

import com.qms.common.constant.SecurityConstants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 品控业务状态机集中注册。后续阶段的样品/不合格单状态机也在此注册。
 */
@Configuration
@RequiredArgsConstructor
public class QmsStateMachineRegistrar {

    private final StateMachineEngine engine;

    private static final String ADMIN = SecurityConstants.ROLE_ADMIN;

    @PostConstruct
    public void register() {
        registerSampling();
        registerSample();
        registerInspectionTask();
        registerDefectCase();
        registerStandardTemplate();
        registerNpiProject();
    }

    /** 新品档位标准评审：DRAFT → 评审中 → 已发布（驳回回草稿）。 */
    private void registerStandardTemplate() {
        engine.register("qc_standard_template", Set.of(
                StateTransition.of("DRAFT", "SUBMIT_REVIEW", "REVIEWING",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("REVIEWING", "REVIEW_REJECT", "DRAFT",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("REVIEWING", "REVIEW_APPROVE", "PUBLISHED",
                        Set.of("QA_MANAGER", ADMIN))
        ));
    }

    /**
     * 新品引入项目主流程：
     * 立项 → 标准评审 → 寻源送样 → 验厂 → 量产 → 外检 → 上市放行 → 已上市；
     * 验厂/外检不通过可回退；任意在途阶段可终止。
     */
    private void registerNpiProject() {
        engine.register("qc_npi_project", Set.of(
                StateTransition.of("DRAFT", "SUBMIT_STD", "STD_REVIEW",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("STD_REVIEW", "REJECT_STD", "DRAFT",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("STD_REVIEW", "APPROVE_STD", "SOURCING",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("SOURCING", "FIX_SUPPLIER", "AUDIT",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("AUDIT", "AUDIT_PASS", "PRODUCING",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("AUDIT", "AUDIT_FAIL", "SOURCING",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PRODUCING", "SEND_EXT", "EXT_TEST",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("EXT_TEST", "TEST_PASS", "LISTING_REVIEW",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("EXT_TEST", "TEST_FAIL", "PRODUCING",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("LISTING_REVIEW", "RELEASE", "LISTED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("DRAFT", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("STD_REVIEW", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("SOURCING", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("AUDIT", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PRODUCING", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("EXT_TEST", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("LISTING_REVIEW", "TERMINATE", "TERMINATED",
                        Set.of("QA_MANAGER", ADMIN))
        ));
    }

    /** 不合格处置单状态机（设计文档3.5）。复检合格/失败事件在复核人同事务下触发，故含 REVIEWER。 */
    private void registerDefectCase() {
        engine.register("qc_defect_case", Set.of(
                StateTransition.of("PENDING_REVIEW", "REVIEW", "PENDING_APPROVAL",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_REVIEW", "CANCEL", "CANCELLED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_APPROVAL", "REJECT", "PENDING_REVIEW",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_APPROVAL", "APPROVE", "PROCESSING",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PROCESSING", "EXECUTE", "PENDING_RECHECK",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PROCESSING", "CONCESSION", "CLOSED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_RECHECK", "RECHECK_PASS", "CLOSED",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_RECHECK", "RECHECK_FAIL", "PROCESSING",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN))
        ));
    }

    private void registerSample() {
        engine.register("qc_sample", Set.of(
                // 留样/备样收样后直接入留样位
                StateTransition.of("PENDING_RECEIVE", "RETAIN", "RETAINING",
                        Set.of("SAMPLER", "QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_RECEIVE", "DISPOSE", "DISPOSED",
                        Set.of("SAMPLER", "QA_MANAGER", ADMIN)),
                // 检验完成后的检验样转留样
                StateTransition.of("IN_INSPECTION", "RETAIN", "RETAINING",
                        Set.of("SAMPLER", "INSPECTOR", "QA_MANAGER", ADMIN)),
                StateTransition.of("IN_INSPECTION", "DISPOSE", "DISPOSED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("RETAINING", "DISPOSE", "DISPOSED",
                        Set.of("SAMPLER", "QA_MANAGER", ADMIN))
        ));
    }

    private void registerSampling() {
        engine.register("qc_sampling", Set.of(
                StateTransition.of("DRAFT", "SUBMIT", "PENDING_RECEIVE",
                        Set.of("SAMPLER", "QA_MANAGER", ADMIN)),
                StateTransition.of("DRAFT", "CANCEL", "CANCELLED",
                        Set.of("SAMPLER", "QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_RECEIVE", "RECEIVE", "RECEIVED",
                        Set.of("SAMPLER", ADMIN)),
                StateTransition.of("PENDING_RECEIVE", "CANCEL", "CANCELLED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("RECEIVED", "ARCHIVE", "ARCHIVED",
                        Set.of("QA_MANAGER", ADMIN))
        ));
    }

    private void registerInspectionTask() {
        engine.register("qc_inspection_task", Set.of(
                StateTransition.of("PENDING_ASSIGN", "ASSIGN", "PENDING_INSPECT",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_INSPECT", "START", "INSPECTING",
                        Set.of("INSPECTOR", ADMIN)),
                StateTransition.of("INSPECTING", "SUBMIT", "PENDING_REVIEW",
                        Set.of("INSPECTOR", ADMIN)),
                StateTransition.of("PENDING_REVIEW", "REJECT", "INSPECTING",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN)),
                StateTransition.of("PENDING_REVIEW", "PASS", "JUDGED",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN)),
                StateTransition.of("JUDGED", "RECHECK", "RECHECKING",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN)),
                StateTransition.of("JUDGED", "CLOSE", "CLOSED",
                        Set.of("QA_MANAGER", ADMIN)),
                StateTransition.of("RECHECKING", "CLOSE", "CLOSED",
                        Set.of("REVIEWER", "QA_MANAGER", ADMIN))
        ));
    }
}
