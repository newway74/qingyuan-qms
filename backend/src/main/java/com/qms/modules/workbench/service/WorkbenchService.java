package com.qms.modules.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.security.LoginUser;
import com.qms.modules.defect.entity.DefectCase;
import com.qms.modules.defect.entity.SupplierRectification;
import com.qms.modules.defect.mapper.DefectCaseMapper;
import com.qms.modules.defect.mapper.SupplierRectificationMapper;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.message.service.MessageService;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.entity.Sampling;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.sampling.mapper.SamplingMapper;
import com.qms.modules.todo.service.TodoService;
import com.qms.modules.workbench.vo.WorkbenchSummary;
import com.qms.modules.workbench.vo.WorkbenchSummary.Card;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色化工作台聚合：卡片数量口径直接取业务表实时统计，保证与数据库状态一致；
 * 待办列表/计数走 qc_todo 聚合入口。
 */
@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private final SamplingMapper samplingMapper;
    private final SampleMapper sampleMapper;
    private final InspectionTaskMapper taskMapper;
    private final DefectCaseMapper defectCaseMapper;
    private final SupplierRectificationMapper rectificationMapper;
    private final TodoService todoService;
    private final MessageService messageService;

    /** SLA 临期预警提前小时数（与定时扫描同源配置） */
    @Value("${qms.alert.sla-warning-hours:24}")
    private long slaWarningHours;

    public WorkbenchSummary summary() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Set<String> roles = loginUser.getRoles() == null ? Set.of() : loginUser.getRoles();
        boolean isAdmin = roles.contains("ADMIN");

        List<Card> cards = new ArrayList<>();
        if (roles.contains("SAMPLER")) {
            cards.addAll(samplerCards());
        }
        if (roles.contains("INSPECTOR")) {
            cards.addAll(inspectorCards(loginUser.getUserId()));
        }
        if (roles.contains("REVIEWER")) {
            cards.addAll(reviewerCards());
        }
        if (roles.contains("QA_MANAGER") || isAdmin) {
            cards.addAll(qaManagerCards(todoService.openCountByType().getOrDefault("ALERT", 0L)));
        }

        return new WorkbenchSummary(
                todoService.openTotal(),
                messageService.unreadCount(),
                todoService.openCountByType(),
                cards);
    }

    // ---------------- 抽样收样岗 ----------------

    private List<Card> samplerCards() {
        long pendingReceive = count(samplingMapper.selectCount(new LambdaQueryWrapper<Sampling>()
                .eq(Sampling::getStatus, "PENDING_RECEIVE")));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayReceived = count(sampleMapper.selectCount(new LambdaQueryWrapper<Sample>()
                .ge(Sample::getReceivedAt, todayStart)));
        return List.of(
                new Card("pendingReceive", "待收样登记", pendingReceive,
                        pendingReceive > 0 ? "warning" : "normal"),
                new Card("todayReceived", "今日新增样品", todayReceived, "normal"));
    }

    // ---------------- 检验员 ----------------

    private List<Card> inspectorCards(Long userId) {
        List<InspectionTask> active = taskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                .eq(InspectionTask::getInspectorId, userId)
                .in(InspectionTask::getStatus, List.of("PENDING_INSPECT", "INSPECTING")));
        long pendingInspect = active.stream().filter(t -> "PENDING_INSPECT".equals(t.getStatus())).count();
        long inspecting = active.stream().filter(t -> "INSPECTING".equals(t.getStatus())).count();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warnLine = now.plusHours(slaWarningHours);
        long slaDueSoon = active.stream()
                .filter(t -> t.getSlaDeadline() != null
                        && t.getSlaDeadline().isAfter(now) && !t.getSlaDeadline().isAfter(warnLine))
                .count();
        long slaOverdue = active.stream()
                .filter(t -> t.getSlaDeadline() != null && t.getSlaDeadline().isBefore(now))
                .count();
        return List.of(
                new Card("myPendingInspect", "我的待检", pendingInspect,
                        pendingInspect > 0 ? "warning" : "normal"),
                new Card("myInspecting", "检验中", inspecting, "normal"),
                new Card("mySlaDueSoon", "SLA 临期（" + slaWarningHours + "h内）", slaDueSoon,
                        slaDueSoon > 0 ? "warning" : "normal"),
                new Card("mySlaOverdue", "SLA 已超期", slaOverdue,
                        slaOverdue > 0 ? "danger" : "normal"));
    }

    // ---------------- 复核判定人 ----------------

    private List<Card> reviewerCards() {
        long pendingReview = count(taskMapper.selectCount(new LambdaQueryWrapper<InspectionTask>()
                .eq(InspectionTask::getStatus, "PENDING_REVIEW")));
        return List.of(
                new Card("pendingReview", "待复核判定", pendingReview,
                        pendingReview > 0 ? "warning" : "normal"));
    }

    // ---------------- 质量主管 ----------------

    private List<Card> qaManagerCards(long alertOpen) {
        long pendingAssign = count(taskMapper.selectCount(new LambdaQueryWrapper<InspectionTask>()
                .eq(InspectionTask::getStatus, "PENDING_ASSIGN")));
        long defectReview = count(defectCaseMapper.selectCount(new LambdaQueryWrapper<DefectCase>()
                .eq(DefectCase::getStatus, "PENDING_REVIEW")));
        long defectApproval = count(defectCaseMapper.selectCount(new LambdaQueryWrapper<DefectCase>()
                .eq(DefectCase::getStatus, "PENDING_APPROVAL")));
        long defectProcessing = count(defectCaseMapper.selectCount(new LambdaQueryWrapper<DefectCase>()
                .in(DefectCase::getStatus, List.of("PROCESSING", "PENDING_RECHECK"))));
        long rectReplied = count(rectificationMapper.selectCount(new LambdaQueryWrapper<SupplierRectification>()
                .eq(SupplierRectification::getStatus, "REPLIED")));
        long rectFollowing = count(rectificationMapper.selectCount(new LambdaQueryWrapper<SupplierRectification>()
                .in(SupplierRectification::getStatus, List.of("ISSUED", "FAILED", "VERIFYING"))));
        List<Card> cards = new ArrayList<>(List.of(
                new Card("pendingAssign", "待分配任务", pendingAssign,
                        pendingAssign > 0 ? "warning" : "normal"),
                new Card("defectReview", "不合格待评审", defectReview,
                        defectReview > 0 ? "warning" : "normal"),
                new Card("defectApproval", "不合格待审批", defectApproval,
                        defectApproval > 0 ? "warning" : "normal"),
                new Card("defectProcessing", "不合格处置中", defectProcessing, "normal"),
                new Card("rectReplied", "整改待验证", rectReplied,
                        rectReplied > 0 ? "warning" : "normal"),
                new Card("rectFollowing", "整改跟进中", rectFollowing, "normal")));
        if (alertOpen > 0) {
            cards.add(new Card("alertOpen", "未处理预警", alertOpen, "danger"));
        }
        return cards;
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
