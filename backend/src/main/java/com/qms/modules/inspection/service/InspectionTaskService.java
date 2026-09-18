package com.qms.modules.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.biz.BizNoGenerator;
import com.qms.framework.statemachine.StateMachineEngine;
import com.qms.modules.defect.entity.DefectCase;
import com.qms.modules.defect.event.RecheckConcludedEvent;
import com.qms.modules.defect.mapper.DefectCaseMapper;
import com.qms.modules.inspection.dto.ResultBatchSaveRequest;
import com.qms.modules.inspection.dto.ResultItemInput;
import com.qms.modules.inspection.dto.ReviewAdjustment;
import com.qms.modules.inspection.dto.TaskAssignRequest;
import com.qms.modules.inspection.dto.TaskReviewRequest;
import com.qms.modules.inspection.engine.InspectionJudgeEngine;
import com.qms.modules.inspection.engine.JudgeItemResult;
import com.qms.modules.inspection.engine.JudgeSummary;
import com.qms.modules.inspection.entity.InspectionReport;
import com.qms.modules.inspection.entity.InspectionResult;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.entity.SignatureRecord;
import com.qms.modules.inspection.mapper.InspectionReportMapper;
import com.qms.modules.inspection.mapper.InspectionResultMapper;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.inspection.mapper.SignatureRecordMapper;
import com.qms.modules.inspection.vo.TaskDetailVO;
import com.qms.modules.inspection.vo.TaskListVO;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.masterdata.service.SupplierQualityService;
import com.qms.modules.process.entity.ProcessNode;
import com.qms.modules.process.mapper.ProcessNodeMapper;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.standard.entity.NetContentTolerance;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.mapper.StandardItemMapper;
import com.qms.modules.standard.mapper.StandardTemplateMapper;
import com.qms.modules.standard.service.StandardTemplateService;
import com.qms.modules.standard.vo.TemplateDetailVO;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import com.qms.modules.system.service.AuthService;
import com.qms.modules.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检验任务执行服务：自动建任务（模板/流程版本快照）→ 分配(SLA) → 逐项录入(自动判定)
 * → 检验员签名提交 → 复核双签(密码二次认证，A类改判硬拦截) → 报告 → 闭环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionTaskService {

    public static final String BIZ_TYPE = "qc_inspection_task";

    private final InspectionTaskMapper taskMapper;
    private final InspectionResultMapper resultMapper;
    private final InspectionReportMapper reportMapper;
    private final SignatureRecordMapper signatureMapper;
    private final SampleMapper sampleMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final StandardTemplateMapper templateMapper;
    private final StandardItemMapper itemMapper;
    private final ProcessNodeMapper processNodeMapper;
    private final SysUserMapper sysUserMapper;
    private final StandardTemplateService templateService;
    private final InspectionJudgeEngine judgeEngine;
    private final SignatureService signatureService;
    private final AuthService authService;
    private final BizNoGenerator bizNoGenerator;
    private final StateMachineEngine stateMachine;
    private final TodoService todoService;
    private final DefectCaseMapper defectCaseMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SupplierQualityService supplierQualityService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 收样时自动建任务（模板/流程版本快照）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public InspectionTask createTaskForSample(Sample sample, Long processDefId,
                                              int roundNo, Long parentTaskId, String recheckReason) {
        ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
        if (sku == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "样品关联SKU不存在: " + sample.getSkuId());
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "样品关联产品不存在");
        }
        // 按品类+包装形态取当前生效模板（精确包装形态优先），任务固化到具体版本主键
        TemplateDetailVO effective = templateService.effective(product.getCategoryId(), sku.getPackageForm());

        InspectionTask task = new InspectionTask();
        task.setTaskNo(bizNoGenerator.next("JC"));
        task.setSampleId(sample.getId());
        task.setTemplateId(effective.getTemplate().getId());
        task.setProcessDefId(processDefId);
        task.setStatus("PENDING_ASSIGN");
        task.setRoundNo(roundNo);
        task.setRecheckParentId(parentTaskId);
        task.setRecheckReason(recheckReason);
        taskMapper.insert(task);

        todoService.createRoleTodo("QA_MANAGER", "APPROVAL", BIZ_TYPE, task.getId(),
                "待分配检验任务：" + product.getProductName() + " " + sku.getSpec(),
                task.getTaskNo(), null);
        log.info("收样自动建检验任务 taskNo={} sample={} template={}",
                task.getTaskNo(), sample.getSampleNo(), effective.getTemplate().getId());
        return task;
    }

    // ------------------------------------------------------------------
    // 查询（多视图）
    // ------------------------------------------------------------------

    public PageResult<TaskListVO> page(PageRequest request, String view, String status, String taskNo) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<InspectionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(taskNo != null && !taskNo.isBlank(), InspectionTask::getTaskNo, taskNo);

        if ("MINE".equalsIgnoreCase(view)) {
            wrapper.and(w -> w
                    .and(i -> i.eq(InspectionTask::getInspectorId, currentUserId)
                            .in(InspectionTask::getStatus, List.of("PENDING_INSPECT", "INSPECTING")))
                    .or(i -> i.eq(InspectionTask::getReviewerId, currentUserId)
                            .eq(InspectionTask::getStatus, "PENDING_REVIEW")));
        } else if ("PENDING_ASSIGN".equalsIgnoreCase(view)) {
            wrapper.eq(InspectionTask::getStatus, "PENDING_ASSIGN");
        } else if (status != null && !status.isBlank()) {
            wrapper.eq(InspectionTask::getStatus, status);
        }
        wrapper.orderByDesc(InspectionTask::getId);
        Page<InspectionTask> page = taskMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<TaskListVO> records = enrichList(page.getRecords());
        return PageResult.of(page, records);
    }

    public TaskDetailVO detail(Long id) {
        InspectionTask task = getRequired(id);
        TaskDetailVO vo = new TaskDetailVO();
        vo.setTask(enrichOne(task));

        StandardTemplate template = templateMapper.selectById(task.getTemplateId());
        vo.setTemplate(template);
        List<StandardItem> items = itemMapper.selectList(new LambdaQueryWrapper<StandardItem>()
                .eq(StandardItem::getTemplateId, task.getTemplateId())
                .orderByAsc(StandardItem::getSort)
                .orderByAsc(StandardItem::getId));
        vo.setItems(items);
        vo.setResults(listResults(id));
        vo.setReport(reportMapper.selectOne(new LambdaQueryWrapper<InspectionReport>()
                .eq(InspectionReport::getTaskId, id)
                .last("LIMIT 1")));

        // 已提交后按最终判定重算综合结论供复核参考
        if (List.of("PENDING_REVIEW", "JUDGED", "RECHECKING", "CLOSED").contains(task.getStatus())
                && !vo.getResults().isEmpty()) {
            vo.setSummary(toSummaryVO(calculateSummary(template, items, vo.getResults())));
        }
        vo.setAllowedActions(stateMachine.allowedActions(
                BIZ_TYPE, task.getStatus(), SecurityUtils.getLoginUser().getRoles()));
        return vo;
    }

    public List<InspectionResult> listResults(Long taskId) {
        return resultMapper.selectList(new LambdaQueryWrapper<InspectionResult>()
                .eq(InspectionResult::getTaskId, taskId)
                .orderByAsc(InspectionResult::getId));
    }

    /** 按角色查启用用户（分配下拉） */
    public List<com.qms.modules.inspection.vo.UserOptionVO> usersByRole(String roleCode) {
        if (!Set.of("INSPECTOR", "REVIEWER", "QA_MANAGER", "SAMPLER").contains(roleCode)) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法角色编码");
        }
        return sysUserMapper.selectActiveUsersByRoleCode(roleCode).stream()
                .map(u -> new com.qms.modules.inspection.vo.UserOptionVO(
                        u.getId(), u.getUsername(), u.getRealName()))
                .toList();
    }

    // ------------------------------------------------------------------
    // 分配
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验任务", action = "ASSIGN", bizType = BIZ_TYPE, bizIdExpr = "#request.taskId")
    public void assign(TaskAssignRequest request) {
        InspectionTask task = getRequired(request.getTaskId());
        AuditContext.putBefore(writeJson(task));
        SysUser inspector = requireActiveUser(request.getInspectorId(), "检验员");
        SysUser reviewer = requireActiveUser(request.getReviewerId(), "复核判定人");
        requireUserHasRole(inspector, "INSPECTOR");
        requireUserHasRole(reviewer, "REVIEWER");
        if (request.getInspectorId().equals(request.getReviewerId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "检验员与复核判定人不能为同一人（双签隔离）");
        }

        String next = stateMachine.nextState(BIZ_TYPE, task.getStatus(), "ASSIGN", currentRoles());
        task.setInspectorId(inspector.getId());
        task.setReviewerId(reviewer.getId());
        task.setAssignedAt(LocalDateTime.now());
        // SLA：取所快照流程版本 INSPECT 节点自然小时
        Integer slaHours = resolveNodeSlaHours(task.getProcessDefId(), "INSPECT");
        if (slaHours != null && slaHours > 0) {
            task.setSlaDeadline(LocalDateTime.now().plusHours(slaHours));
        }
        task.setStatus(next);
        updateWithLock(task);

        todoService.handle(BIZ_TYPE, task.getId());
        todoService.createUserTodo(inspector.getId(), "INSPECT", BIZ_TYPE, task.getId(),
                "待检任务：" + task.getTaskNo(), task.getTaskNo(), task.getSlaDeadline());
    }

    // ------------------------------------------------------------------
    // 开始检验 / 结果录入
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验任务", action = "START", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void start(Long id) {
        InspectionTask task = getRequired(id);
        assertAssignedInspector(task);
        String next = stateMachine.nextState(BIZ_TYPE, task.getStatus(), "START", currentRoles());
        task.setStatus(next);
        task.setStartedAt(LocalDateTime.now());
        updateWithLock(task);
        ensureResultsInitialized(task);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验结果", action = "SAVE", bizType = BIZ_TYPE, bizIdExpr = "#taskId")
    public void saveResults(Long taskId, ResultBatchSaveRequest request) {
        InspectionTask task = getRequired(taskId);
        if (!"INSPECTING".equals(task.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅检验中的任务可录入结果");
        }
        assertAssignedInspector(task);
        ensureResultsInitialized(task);
        List<InspectionResult> existing = listResults(taskId);
        Map<Long, InspectionResult> byItemId = new HashMap<>();
        Map<Long, InspectionResult> byResultId = new HashMap<>();
        for (InspectionResult r : existing) {
            byItemId.put(r.getItemId(), r);
            byResultId.put(r.getId(), r);
        }
        List<StandardItem> items = listTemplateItems(task);
        Map<Long, StandardItem> itemMap = new HashMap<>();
        for (StandardItem item : items) {
            itemMap.put(item.getId(), item);
        }

        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityUtils.getCurrentUserId();
        for (ResultItemInput input : request.getResults()) {
            InspectionResult result = input.getResultId() == null
                    ? byItemId.get(input.getItemId()) : byResultId.get(input.getResultId());
            if (result == null) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "检验项不存在或不属于该任务: " + input.getItemId());
            }
            StandardItem item = itemMap.get(result.getItemId());
            result.setQualitativeValue(blankToNull(input.getQualitativeValue()));
            result.setQuantitativeValue(input.getQuantitativeValue());
            result.setDocAttachmentId(input.getDocAttachmentId());
            result.setRemark(blankToNull(input.getRemark()));
            NetJudge net = resolveNetJudge(item, task);
            JudgeItemResult judged = InspectionJudgeEngine.judgeItem(item,
                    result.getQualitativeValue(), result.getQuantitativeValue(),
                    result.getDocAttachmentId(), net.tolerance(), net.nominal());
            result.setAutoJudgement(judged.getJudgement());
            // 检验员录入阶段最终判定跟随自动判定；复核阶段才允许改判
            result.setFinalJudgement(judged.getJudgement());
            result.setReviewNote(null);
            result.setJudgeSnapshot(judged.getSnapshot());
            result.setEnteredBy(userId);
            result.setEnteredAt(now);
            resultMapper.updateById(result);
        }
    }

    // ------------------------------------------------------------------
    // 检验员签名提交
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验任务", action = "SUBMIT", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public SignatureRecord submit(Long id, String password, String ip, String userAgent) {
        InspectionTask task = getRequired(id);
        assertAssignedInspector(task);
        ensureResultsInitialized(task);
        // 密码二次认证
        authService.verifyPassword(SecurityUtils.getLoginUser(), password);

        List<InspectionResult> results = listResults(id);
        List<StandardItem> items = listTemplateItems(task);
        Map<Long, StandardItem> itemMap = new HashMap<>();
        items.forEach(i -> itemMap.put(i.getId(), i));

        // 必检项校验 + 提交时全量自动判定固化
        List<String> missing = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (InspectionResult result : results) {
            StandardItem item = itemMap.get(result.getItemId());
            boolean required = item.getRequiredFlag() != null && item.getRequiredFlag() == 1;
            boolean entered = switch (item.getResultType()) {
                case "QUALITATIVE" -> "PASS".equals(result.getQualitativeValue())
                        || "FAIL".equals(result.getQualitativeValue());
                case "QUANTITATIVE" -> result.getQuantitativeValue() != null;
                case "DOCUMENT" -> result.getDocAttachmentId() != null;
                default -> false;
            };
            if (required && !entered) {
                missing.add(item.getItemName());
            }
            NetJudge net = resolveNetJudge(item, task);
            JudgeItemResult judged = InspectionJudgeEngine.judgeItem(item,
                    result.getQualitativeValue(), result.getQuantitativeValue(),
                    result.getDocAttachmentId(), net.tolerance(), net.nominal());
            result.setAutoJudgement(judged.getJudgement());
            if (result.getFinalJudgement() == null || "NONE".equals(result.getFinalJudgement())) {
                result.setFinalJudgement(judged.getJudgement());
            }
            result.setJudgeSnapshot(judged.getSnapshot());
            result.setEnteredBy(SecurityUtils.getCurrentUserId());
            result.setEnteredAt(now);
            resultMapper.updateById(result);
        }
        if (!missing.isEmpty()) {
            throw new BizException(ResultCode.BIZ_REQUIRED_ITEM_MISSING,
                    "存在未录入的必检项：" + String.join("、", missing));
        }

        String next = stateMachine.nextState(BIZ_TYPE, task.getStatus(), "SUBMIT", currentRoles());
        task.setStatus(next);
        task.setSubmittedAt(now);
        updateWithLock(task);

        // 检验员电子签名（快照哈希 + IP + UA + 服务端时间）
        Map<String, Object> signSnapshot = buildSubmitSnapshot(task, results);
        SignatureRecord record = signatureService.record(
                "TASK_SUBMIT", task.getId(), "检验员提交复核签名", signSnapshot, ip, userAgent);

        todoService.handle(BIZ_TYPE, task.getId());
        todoService.createUserTodo(task.getReviewerId(), "REVIEW", BIZ_TYPE, task.getId(),
                "待复核任务：" + task.getTaskNo(), task.getTaskNo(), task.getSlaDeadline());
        return record;
    }

    // ------------------------------------------------------------------
    // 复核判定（通过/驳回，双签）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验任务", action = "REVIEW", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void review(Long id, TaskReviewRequest request, String ip, String userAgent) {
        InspectionTask task = getRequired(id);
        if ("REJECT".equalsIgnoreCase(request.getAction())) {
            reject(task, request);
            return;
        }
        if (!"PASS".equalsIgnoreCase(request.getAction())) {
            throw new BizException(ResultCode.PARAM_INVALID, "复核动作仅支持 PASS/REJECT");
        }
        pass(task, request, ip, userAgent);
    }

    private void reject(InspectionTask task, TaskReviewRequest request) {
        if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "驳回必须填写原因");
        }
        AuditContext.putBefore(writeJson(task));
        String next = stateMachine.nextState(BIZ_TYPE, task.getStatus(), "REJECT", currentRoles());
        task.setStatus(next);
        task.setReviewRejectReason(request.getRejectReason().trim());
        updateWithLock(task);
        todoService.handle(BIZ_TYPE, task.getId());
        todoService.createUserTodo(task.getInspectorId(), "INSPECT", BIZ_TYPE, task.getId(),
                "复核驳回需整改：" + task.getTaskNo(), task.getTaskNo(), task.getSlaDeadline());
    }

    private void pass(InspectionTask task, TaskReviewRequest request, String ip, String userAgent) {
        authService.verifyPassword(SecurityUtils.getLoginUser(), request.getPassword());
        AuditContext.putBefore(writeJson(task));

        List<InspectionResult> results = listResults(task.getId());
        List<StandardItem> items = listTemplateItems(task);
        Map<Long, StandardItem> itemMap = new HashMap<>();
        items.forEach(i -> itemMap.put(i.getId(), i));
        Map<Long, InspectionResult> resultMap = new HashMap<>();
        results.forEach(r -> resultMap.put(r.getId(), r));

        // 复核改判：仅 B/C 类且必须填理由；A 类（含一票否决）硬拦截
        if (request.getAdjustments() != null) {
            for (ReviewAdjustment adj : request.getAdjustments()) {
                InspectionResult result = resultMap.get(adj.getResultId());
                if (result == null) {
                    throw new BizException(ResultCode.PARAM_INVALID, "改判检验项不存在: " + adj.getResultId());
                }
                StandardItem item = itemMap.get(result.getItemId());
                if ("A".equals(item.getDefectLevel())) {
                    throw new BizException(ResultCode.BIZ_JUDGE_VETO_FAIL,
                            "严重A类项[" + item.getItemName() + "]不合格，禁止改判合格或让步");
                }
                if (!"PASS".equals(adj.getFinalJudgement()) && !"FAIL".equals(adj.getFinalJudgement())) {
                    throw new BizException(ResultCode.PARAM_INVALID, "改判结论仅支持 PASS/FAIL");
                }
                if (adj.getReviewNote() == null || adj.getReviewNote().isBlank()) {
                    throw new BizException(ResultCode.PARAM_MISSING,
                            "检验项[" + item.getItemName() + "]改判必须填写理由");
                }
                result.setFinalJudgement(adj.getFinalJudgement());
                result.setReviewNote(adj.getReviewNote().trim());
                resultMapper.updateById(result);
            }
        }

        StandardTemplate template = templateMapper.selectById(task.getTemplateId());
        JudgeSummary summary = calculateSummary(template, items, results);

        // 最终结论必须与服务端综合判定一致：A类/超限值强制不合格（前端传其他结论直接拒绝）；
        // 让步仅在模板允许且存在B/C类不合格时由复核人选择；合格场景不能判不合格。
        String requested = request.getConclusion();
        String conclusion;
        if (summary.isUnqualified()) {
            if (requested != null && !"UNQUALIFIED".equals(requested)) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "综合判定为不合格（A类/缺陷数超限），最终结论必须为不合格或待复检");
            }
            conclusion = "UNQUALIFIED";
        } else if ("CONCESSION".equals(requested)) {
            if (!summary.isConcessionPossible()) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "当前模板/缺陷情况不允许让步接收，或不存在B/C类不合格项");
            }
            conclusion = "CONCESSION";
        } else if ("UNQUALIFIED".equals(requested)) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "综合判定无不合格项，不能作出不合格结论");
        } else {
            conclusion = "QUALIFIED";
        }

        String next = stateMachine.nextState(BIZ_TYPE, task.getStatus(), "PASS", currentRoles());
        task.setStatus(next);
        task.setReviewedAt(LocalDateTime.now());
        updateWithLock(task);

        // 复核人电子签名
        Map<String, Object> reviewSnapshot = buildReviewSnapshot(task, results, conclusion, summary);
        SignatureRecord reviewerSign = signatureService.record(
                "TASK_REVIEW", task.getId(), "复核判定双签", reviewSnapshot, ip, userAgent);

        // 检验员提交时签名（只增表中取最新一次 TASK_SUBMIT）
        SignatureRecord inspectorSign = signatureMapper.selectOne(new LambdaQueryWrapper<SignatureRecord>()
                .eq(SignatureRecord::getBizType, "TASK_SUBMIT")
                .eq(SignatureRecord::getBizId, task.getId())
                .orderByDesc(SignatureRecord::getId)
                .last("LIMIT 1"));
        if (inspectorSign == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "缺少检验员提交签名，无法完成双签");
        }

        createSignedReport(task, template, summary, conclusion, inspectorSign, reviewerSign);

        // 复检子任务复核完成：发布结论事件，由不合格闭环模块同事务回写处置单/整改单
        if (task.getRecheckParentId() != null) {
            eventPublisher.publishEvent(new RecheckConcludedEvent(
                    task.getId(), task.getRecheckParentId(), !"UNQUALIFIED".equals(conclusion)));
        }

        todoService.handle(BIZ_TYPE, task.getId());
        if ("UNQUALIFIED".equals(conclusion)) {
            todoService.createRoleTodo("QA_MANAGER", "DEFECT", BIZ_TYPE, task.getId(),
                    "不合格待处置：" + task.getTaskNo(), task.getTaskNo(), null);
        } else {
            todoService.createRoleTodo("QA_MANAGER", "APPROVAL", BIZ_TYPE, task.getId(),
                    "待闭环归档：" + task.getTaskNo(), task.getTaskNo(), null);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验任务", action = "CLOSE", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void close(Long id) {
        InspectionTask task = getRequired(id);
        AuditContext.putBefore(writeJson(task));
        InspectionReport report = reportMapper.selectOne(new LambdaQueryWrapper<InspectionReport>()
                .eq(InspectionReport::getTaskId, id).last("LIMIT 1"));
        if (report == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "任务尚未生成检验报告，不能闭环");
        }
        if ("UNQUALIFIED".equals(report.getConclusion())) {
            DefectCase defectCase = defectCaseMapper.selectOne(new LambdaQueryWrapper<DefectCase>()
                    .eq(DefectCase::getTaskId, id).last("LIMIT 1"));
            if (defectCase == null || !"CLOSED".equals(defectCase.getStatus())) {
                throw new BizException(ResultCode.BIZ_STATE_INVALID,
                        "不合格任务须完成不合格处置/复检闭环（不合格单已闭环）后才能关闭");
            }
        }
        String next = stateMachine.nextState(BIZ_TYPE, task.getStatus(), "CLOSE", currentRoles());
        task.setStatus(next);
        updateWithLock(task);
        todoService.handle(BIZ_TYPE, task.getId());
        // 合格任务闭环：沉淀供应商当期评级（不合格链路由不合格单闭环时沉淀）
        if (!"UNQUALIFIED".equals(report.getConclusion())) {
            supplierQualityService.settleBySample(task.getSampleId());
        }
    }

    // ------------------------------------------------------------------
    // 发起复检（原任务 JUDGED→RECHECKING；新建子任务，原任务/原报告不可变）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "检验任务", action = "RECHECK", bizType = BIZ_TYPE, bizIdExpr = "#parentId")
    public InspectionTask recheck(Long parentId, String reason) {
        InspectionTask parent = getRequired(parentId);
        return startRecheck(parent, reason);
    }

    /**
     * 原任务转复检中并创建复检子任务（同事务）：
     * 固化原模板/流程版本快照、复制原检验员/复核人分配与 SLA、默认带出原结果供复检核对。
     */
    public InspectionTask startRecheck(InspectionTask parent, String reason) {
        if (parent.getRecheckParentId() != null) {
            throw new BizException(ResultCode.PARAM_INVALID, "复检任务不能再次发起复检");
        }
        Long openChildren = taskMapper.selectCount(new LambdaQueryWrapper<InspectionTask>()
                .eq(InspectionTask::getRecheckParentId, parent.getId())
                .notIn(InspectionTask::getStatus, List.of("CLOSED")));
        if (openChildren != null && openChildren > 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "该任务已存在进行中的复检任务");
        }
        AuditContext.putBefore(writeJson(parent));
        String next = stateMachine.nextState(BIZ_TYPE, parent.getStatus(), "RECHECK", currentRoles());
        parent.setStatus(next);
        updateWithLock(parent);

        Sample sample = sampleMapper.selectById(parent.getSampleId());
        LocalDateTime now = LocalDateTime.now();
        InspectionTask child = new InspectionTask();
        child.setTaskNo(bizNoGenerator.next("JC"));
        child.setSampleId(parent.getSampleId());
        child.setTemplateId(parent.getTemplateId());
        child.setProcessDefId(parent.getProcessDefId());
        child.setInspectorId(parent.getInspectorId());
        child.setReviewerId(parent.getReviewerId());
        child.setAssignedAt(now);
        child.setStatus("PENDING_INSPECT");
        child.setRoundNo(parent.getRoundNo() + 1);
        child.setRecheckParentId(parent.getId());
        child.setRecheckReason(reason == null || reason.isBlank() ? null : reason.trim());
        Integer slaHours = resolveNodeSlaHours(parent.getProcessDefId(), "INSPECT");
        if (slaHours != null && slaHours > 0) {
            child.setSlaDeadline(now.plusHours(slaHours));
        }
        taskMapper.insert(child);

        // 默认带出原结果（值可改，判定随复检录入重算）
        List<InspectionResult> originResults = listResults(parent.getId());
        for (InspectionResult origin : originResults) {
            InspectionResult copy = new InspectionResult();
            copy.setTaskId(child.getId());
            copy.setItemId(origin.getItemId());
            copy.setGroupCode(origin.getGroupCode());
            copy.setItemName(origin.getItemName());
            copy.setResultType(origin.getResultType());
            copy.setUnit(origin.getUnit());
            copy.setQualitativeValue(origin.getQualitativeValue());
            copy.setQuantitativeValue(origin.getQuantitativeValue());
            copy.setDocAttachmentId(origin.getDocAttachmentId());
            copy.setAutoJudgement("NONE");
            copy.setFinalJudgement("NONE");
            copy.setJudgeSnapshot(origin.getJudgeSnapshot());
            resultMapper.insert(copy);
        }

        if (sample != null) {
            ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
            String title = "复检任务：" + child.getTaskNo()
                    + (sku == null ? "" : "（" + sku.getSpec() + "）");
            if (child.getInspectorId() != null) {
                todoService.createUserTodo(child.getInspectorId(), "INSPECT", BIZ_TYPE, child.getId(),
                        title, child.getTaskNo(), child.getSlaDeadline());
            }
        }
        log.info("发起复检 parent={} child={} round={}",
                parent.getTaskNo(), child.getTaskNo(), child.getRoundNo());
        return child;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void createSignedReport(InspectionTask task, StandardTemplate template,
                                    JudgeSummary summary, String conclusion,
                                    SignatureRecord inspectorSign, SignatureRecord reviewerSign) {
        InspectionReport exist = reportMapper.selectOne(new LambdaQueryWrapper<InspectionReport>()
                .eq(InspectionReport::getTaskId, task.getId()).last("LIMIT 1"));
        if (exist != null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "任务已生成报告，不可重复判定");
        }
        InspectionReport report = new InspectionReport();
        report.setTaskId(task.getId());
        report.setReportNo(bizNoGenerator.next("BG"));
        report.setConclusion(conclusion);
        report.setAFailCount(summary.getAFailCount());
        report.setBFailCount(summary.getBFailCount());
        report.setCFailCount(summary.getCFailCount());
        report.setDefectSummary(summary.getFailures());
        report.setInspectorSignHash(inspectorSign.getSnapshotHash());
        report.setInspectorSignedAt(inspectorSign.getSignedAt());
        report.setReviewerSignHash(reviewerSign.getSnapshotHash());
        report.setReviewerSignedAt(reviewerSign.getSignedAt());
        // 双签完成即 SIGNED；签发 ISSUED 后只读并留存 PDF
        report.setStatus("SIGNED");
        reportMapper.insert(report);
    }

    private void ensureResultsInitialized(InspectionTask task) {
        Long count = resultMapper.selectCount(new LambdaQueryWrapper<InspectionResult>()
                .eq(InspectionResult::getTaskId, task.getId()));
        if (count != null && count > 0) {
            return;
        }
        List<StandardItem> items = listTemplateItems(task);
        for (StandardItem item : items) {
            InspectionResult result = new InspectionResult();
            result.setTaskId(task.getId());
            result.setItemId(item.getId());
            result.setGroupCode(item.getGroupCode());
            result.setItemName(item.getItemName());
            result.setResultType(item.getResultType());
            result.setUnit(item.getUnit());
            result.setAutoJudgement("NONE");
            result.setFinalJudgement("NONE");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("itemId", item.getId());
            snapshot.put("defectLevel", item.getDefectLevel());
            snapshot.put("veto", item.getVetoFlag());
            snapshot.put("required", item.getRequiredFlag());
            result.setJudgeSnapshot(snapshot);
            resultMapper.insert(result);
        }
    }

    private List<StandardItem> listTemplateItems(InspectionTask task) {
        return itemMapper.selectList(new LambdaQueryWrapper<StandardItem>()
                .eq(StandardItem::getTemplateId, task.getTemplateId())
                .orderByAsc(StandardItem::getSort)
                .orderByAsc(StandardItem::getId));
    }

    /** JJF1070 判定上下文：有效标称值 + 分档允许短缺量 */
    private record NetJudge(BigDecimal nominal, NetContentTolerance tolerance) {
        static NetJudge NONE = new NetJudge(null, null);
    }

    /**
     * 净含量 JJF1070：有效标称值优先取检验项配置的 nominal_value；
     * 未配置时（标准模板按品类复用）取样品关联 SKU 的净含量，并解析分档。
     */
    private NetJudge resolveNetJudge(StandardItem item, InspectionTask task) {
        if (!"JJF1070".equalsIgnoreCase(item.getToleranceRule())) {
            return NetJudge.NONE;
        }
        BigDecimal nominal = item.getNominalValue();
        if (nominal == null) {
            Sample sample = sampleMapper.selectById(task.getSampleId());
            if (sample != null) {
                ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
                if (sku != null) {
                    nominal = sku.getNetContent();
                }
            }
        }
        if (nominal == null) {
            return NetJudge.NONE;
        }
        return new NetJudge(nominal, judgeEngine.resolveTolerance(nominal));
    }

    private JudgeSummary calculateSummary(StandardTemplate template,
                                          List<StandardItem> items,
                                          List<InspectionResult> results) {
        Map<Long, String> judgements = new HashMap<>();
        for (InspectionResult r : results) {
            judgements.put(r.getItemId(),
                    r.getFinalJudgement() == null ? r.getAutoJudgement() : r.getFinalJudgement());
        }
        return InspectionJudgeEngine.summarize(items, judgements, template);
    }

    private TaskDetailVO.JudgeSummaryVO toSummaryVO(JudgeSummary summary) {
        TaskDetailVO.JudgeSummaryVO vo = new TaskDetailVO.JudgeSummaryVO();
        vo.setAFailCount(summary.getAFailCount());
        vo.setBFailCount(summary.getBFailCount());
        vo.setCFailCount(summary.getCFailCount());
        vo.setVetoFail(summary.isVetoFail());
        vo.setUnqualified(summary.isUnqualified());
        vo.setConcessionPossible(summary.isConcessionPossible());
        vo.setSuggestedConclusion(summary.getSuggestedConclusion());
        return vo;
    }

    private Map<String, Object> buildSubmitSnapshot(InspectionTask task, List<InspectionResult> results) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("taskNo", task.getTaskNo());
        snapshot.put("templateId", task.getTemplateId());
        snapshot.put("results", results.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resultId", r.getId());
            m.put("itemId", r.getItemId());
            m.put("resultType", r.getResultType());
            m.put("qualitativeValue", r.getQualitativeValue());
            m.put("quantitativeValue", r.getQuantitativeValue());
            m.put("docAttachmentId", r.getDocAttachmentId());
            m.put("autoJudgement", r.getAutoJudgement());
            return m;
        }).toList());
        return snapshot;
    }

    private Map<String, Object> buildReviewSnapshot(InspectionTask task, List<InspectionResult> results,
                                                    String conclusion, JudgeSummary summary) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("taskNo", task.getTaskNo());
        snapshot.put("conclusion", conclusion);
        snapshot.put("aFailCount", summary.getAFailCount());
        snapshot.put("bFailCount", summary.getBFailCount());
        snapshot.put("cFailCount", summary.getCFailCount());
        snapshot.put("vetoFail", summary.isVetoFail());
        snapshot.put("results", results.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resultId", r.getId());
            m.put("itemId", r.getItemId());
            m.put("finalJudgement", r.getFinalJudgement());
            m.put("reviewNote", r.getReviewNote());
            return m;
        }).toList());
        return snapshot;
    }

    private Integer resolveNodeSlaHours(Long processDefId, String nodeCode) {
        if (processDefId == null) {
            return null;
        }
        ProcessNode node = processNodeMapper.selectOne(new LambdaQueryWrapper<ProcessNode>()
                .eq(ProcessNode::getProcessDefId, processDefId)
                .eq(ProcessNode::getNodeCode, nodeCode)
                .last("LIMIT 1"));
        return node == null ? null : node.getSlaHours();
    }

    private void assertAssignedInspector(InspectionTask task) {
        LoginUserRoles roles = currentUserRoles();
        if (roles.roles.contains("ADMIN") || roles.roles.contains("QA_MANAGER")) {
            return;
        }
        if (!roles.userId.equals(task.getInspectorId())) {
            throw new BizException(ResultCode.AUTH_FORBIDDEN, "任务未分配给当前检验员");
        }
    }

    private record LoginUserRoles(Long userId, Set<String> roles) {
    }

    private LoginUserRoles currentUserRoles() {
        return new LoginUserRoles(SecurityUtils.getCurrentUserId(),
                SecurityUtils.getLoginUser().getRoles());
    }

    private Set<String> currentRoles() {
        return SecurityUtils.getLoginUser().getRoles();
    }

    private SysUser requireActiveUser(Long userId, String label) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_MISSING, label + "不能为空");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, label + "不存在或已停用");
        }
        return user;
    }

    private void requireUserHasRole(SysUser user, String roleCode) {
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        if (roles == null || !roles.contains(roleCode)) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    user.getRealName() + " 不具备角色 " + roleCode);
        }
    }

    public InspectionTask getRequired(Long id) {
        InspectionTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "检验任务不存在");
        }
        return task;
    }

    private void updateWithLock(InspectionTask task) {
        int rows = taskMapper.updateById(task);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    private List<TaskListVO> enrichList(List<InspectionTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, Sample> sampleCache = new HashMap<>();
        Map<Long, ProductSku> skuCache = new HashMap<>();
        Map<Long, Product> productCache = new HashMap<>();
        Map<Long, StandardTemplate> templateCache = new HashMap<>();
        Map<Long, SysUser> userCache = new HashMap<>();
        Map<Long, Long> resultCountCache = new HashMap<>();
        return tasks.stream().map(task -> {
            TaskListVO vo = new TaskListVO();
            org.springframework.beans.BeanUtils.copyProperties(task, vo);
            Sample sample = sampleCache.computeIfAbsent(task.getSampleId(), sampleMapper::selectById);
            if (sample != null) {
                vo.setSampleNo(sample.getSampleNo());
                vo.setBatchNo(sample.getBatchNo());
                ProductSku sku = skuCache.computeIfAbsent(sample.getSkuId(), productSkuMapper::selectById);
                if (sku != null) {
                    vo.setSkuId(sku.getId());
                    vo.setSkuCode(sku.getSkuCode());
                    vo.setSpec(sku.getSpec());
                    vo.setPackageForm(sku.getPackageForm());
                    Product product = productCache.computeIfAbsent(sku.getProductId(), productMapper::selectById);
                    if (product != null) {
                        vo.setProductName(product.getProductName());
                    }
                }
            }
            StandardTemplate template = templateCache.computeIfAbsent(task.getTemplateId(),
                    templateMapper::selectById);
            if (template != null) {
                vo.setTemplateName(template.getTemplateName());
                vo.setTemplateVersion(template.getVersion());
            }
            if (task.getInspectorId() != null) {
                SysUser inspector = userCache.computeIfAbsent(task.getInspectorId(), sysUserMapper::selectById);
                vo.setInspectorName(inspector == null ? null : inspector.getRealName());
            }
            if (task.getReviewerId() != null) {
                SysUser reviewer = userCache.computeIfAbsent(task.getReviewerId(), sysUserMapper::selectById);
                vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
            }
            Long cnt = resultCountCache.computeIfAbsent(task.getId(), k -> resultMapper.selectCount(
                    new LambdaQueryWrapper<InspectionResult>().eq(InspectionResult::getTaskId, k)));
            vo.setResultCount(cnt == null ? 0 : cnt.intValue());
            return vo;
        }).toList();
    }

    private TaskListVO enrichOne(InspectionTask task) {
        return enrichList(List.of(task)).get(0);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("审计快照序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
