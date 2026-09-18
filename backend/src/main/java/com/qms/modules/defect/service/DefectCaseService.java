package com.qms.modules.defect.service;

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
import com.qms.modules.defect.dto.CaseApproveRequest;
import com.qms.modules.defect.dto.CaseCancelRequest;
import com.qms.modules.defect.dto.CaseConcessionRequest;
import com.qms.modules.defect.dto.CaseCreateRequest;
import com.qms.modules.defect.dto.CaseExecuteRequest;
import com.qms.modules.defect.dto.CaseReviewRequest;
import com.qms.modules.defect.entity.DefectApproval;
import com.qms.modules.defect.entity.DefectCase;
import com.qms.modules.defect.entity.DefectItem;
import com.qms.modules.defect.entity.SupplierRectification;
import com.qms.modules.defect.event.RecheckConcludedEvent;
import com.qms.modules.defect.event.ReportIssuedEvent;
import com.qms.modules.defect.mapper.DefectApprovalMapper;
import com.qms.modules.defect.mapper.DefectCaseMapper;
import com.qms.modules.defect.mapper.DefectItemMapper;
import com.qms.modules.defect.mapper.SupplierRectificationMapper;
import com.qms.modules.defect.vo.CaseDetailVO;
import com.qms.modules.defect.vo.CaseListVO;
import com.qms.modules.defect.vo.RectificationVO;
import com.qms.modules.inspection.entity.InspectionReport;
import com.qms.modules.inspection.entity.InspectionResult;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.mapper.InspectionReportMapper;
import com.qms.modules.inspection.mapper.InspectionResultMapper;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.inspection.service.SignatureService;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.masterdata.service.SupplierQualityService;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.mapper.StandardItemMapper;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import com.qms.modules.system.service.AuthService;
import com.qms.modules.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 不合格处置单服务：报告签发不合格自动建档（缺陷明细一一对应）→ 质量评审 → 主管审批（签名）
 * → 处置执行/让步接收（A类硬拦截）→ 复检回写闭环；全程只增时间线留痕。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefectCaseService {

    public static final String BIZ_TYPE = "qc_defect_case";

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> DISPOSITIONS =
            Set.of("RETURN", "OFF_SHELF", "DESTROY", "EXCHANGE", "RECTIFY", "CONCESSION");

    private final DefectCaseMapper caseMapper;
    private final DefectItemMapper itemMapper;
    private final DefectApprovalMapper approvalMapper;
    private final SupplierRectificationMapper rectificationMapper;
    private final InspectionReportMapper reportMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionResultMapper resultMapper;
    private final StandardItemMapper standardItemMapper;
    private final SampleMapper sampleMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper sysUserMapper;
    private final BizNoGenerator bizNoGenerator;
    private final StateMachineEngine stateMachine;
    private final SignatureService signatureService;
    private final AuthService authService;
    private final TodoService todoService;
    private final SupplierQualityService supplierQualityService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 自动/手动建档
    // ------------------------------------------------------------------

    /** 报告签发时调用：UNQUALIFIED/CONCESSION 报告事务内自动建档，合格不产生；幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public void createFromIssuedReport(InspectionReport report) {
        if ("QUALIFIED".equals(report.getConclusion())) {
            return;
        }
        Long exists = caseMapper.selectCount(new LambdaQueryWrapper<DefectCase>()
                .eq(DefectCase::getReportId, report.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        doCreate(report, "报告签发自动建档（结论：" + report.getConclusion() + "）");
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "CREATE", bizType = BIZ_TYPE)
    public Long manualCreate(CaseCreateRequest request) {
        InspectionReport report = reportMapper.selectById(request.getReportId());
        if (report == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "检验报告不存在");
        }
        if (!"SIGNED".equals(report.getStatus()) && !"ISSUED".equals(report.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅已双签/已签发报告可建立不合格单");
        }
        Long exists = caseMapper.selectCount(new LambdaQueryWrapper<DefectCase>()
                .eq(DefectCase::getReportId, report.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "该报告已存在不合格处置单");
        }
        DefectCase created = doCreate(report,
                request.getComment() == null || request.getComment().isBlank()
                        ? "手动建档" : request.getComment().trim());
        return created.getId();
    }

    private DefectCase doCreate(InspectionReport report, String comment) {
        InspectionTask task = taskMapper.selectById(report.getTaskId());
        if (task == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "报告关联检验任务不存在");
        }

        DefectCase caseEntity = new DefectCase();
        caseEntity.setCaseNo(bizNoGenerator.next("BH"));
        caseEntity.setReportId(report.getId());
        caseEntity.setTaskId(task.getId());
        caseEntity.setSampleId(task.getSampleId());
        caseEntity.setStatus("PENDING_REVIEW");
        caseMapper.insert(caseEntity);

        // 缺陷明细与最终判定不合格的检验结果一一对应
        List<InspectionResult> results = resultMapper.selectList(new LambdaQueryWrapper<InspectionResult>()
                .eq(InspectionResult::getTaskId, task.getId()));
        List<StandardItem> items = standardItemMapper.selectList(new LambdaQueryWrapper<StandardItem>()
                .eq(StandardItem::getTemplateId, task.getTemplateId()));
        Map<Long, StandardItem> itemMap = new HashMap<>();
        items.forEach(i -> itemMap.put(i.getId(), i));

        String maxSeverity = null;
        for (InspectionResult result : results) {
            String judgement = result.getFinalJudgement() == null
                    ? result.getAutoJudgement() : result.getFinalJudgement();
            if (!"FAIL".equals(judgement)) {
                continue;
            }
            StandardItem item = itemMap.get(result.getItemId());
            String level = item == null ? "C" : item.getDefectLevel();
            if (maxSeverity == null || severityRank(level) < severityRank(maxSeverity)) {
                maxSeverity = level;
            }
            DefectItem defectItem = new DefectItem();
            defectItem.setCaseId(caseEntity.getId());
            defectItem.setResultId(result.getId());
            defectItem.setDefectLevel(level);
            defectItem.setItemName(result.getItemName());
            defectItem.setFailDesc(buildFailDesc(result, item));
            itemMapper.insert(defectItem);
        }
        if (maxSeverity == null) {
            // 报告判不合格但无逐项FAIL（异常数据），按报告计数兜底定级
            maxSeverity = report.getAFailCount() > 0 ? "A"
                    : report.getBFailCount() > 0 ? "B" : "C";
        }
        caseEntity.setMaxSeverity(maxSeverity);
        caseMapper.updateById(caseEntity);

        Map<String, Object> after = snapshot(caseEntity);
        appendTimeline(caseEntity.getId(), "REVIEW", "CREATE", comment, null, after, null);

        todoService.createRoleTodo("QA_MANAGER", "DEFECT", BIZ_TYPE, caseEntity.getId(),
                "不合格待评审：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
        log.info("不合格处置单建档 caseNo={} report={} maxSeverity={}",
                caseEntity.getCaseNo(), report.getReportNo(), maxSeverity);
        return caseEntity;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public PageResult<CaseListVO> page(PageRequest request, String caseNo, String status, String maxSeverity) {
        LambdaQueryWrapper<DefectCase> wrapper = new LambdaQueryWrapper<DefectCase>()
                .like(caseNo != null && !caseNo.isBlank(), DefectCase::getCaseNo, caseNo)
                .eq(status != null && !status.isBlank(), DefectCase::getStatus, status)
                .eq(maxSeverity != null && !maxSeverity.isBlank(), DefectCase::getMaxSeverity, maxSeverity)
                .orderByDesc(DefectCase::getId);
        Page<DefectCase> page = caseMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toListVO).toList());
    }

    public CaseDetailVO detail(Long id) {
        DefectCase caseEntity = getRequired(id);
        CaseDetailVO vo = new CaseDetailVO();
        CaseListVO base = toListVO(caseEntity);
        vo.setCaseObj(caseEntity);
        vo.setReportNo(base.getReportNo());
        vo.setTaskNo(base.getTaskNo());
        vo.setSampleNo(base.getSampleNo());
        vo.setProductName(base.getProductName());
        vo.setSpec(base.getSpec());
        vo.setBatchNo(base.getBatchNo());
        vo.setSupplierName(base.getSupplierName());
        vo.setConclusion(base.getConclusion());
        vo.setAFailCount(base.getAFailCount());
        vo.setBFailCount(base.getBFailCount());
        vo.setCFailCount(base.getCFailCount());
        vo.setIssuedAt(base.getIssuedAt() == null ? null : base.getIssuedAt().format(DT_FMT));

        InspectionTask task = caseEntity.getTaskId() == null ? null
                : taskMapper.selectById(caseEntity.getTaskId());
        if (task != null) {
            Sample sample = sampleMapper.selectById(task.getSampleId());
            if (sample != null) {
                ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
                if (sku != null) {
                    Product product = productMapper.selectById(sku.getProductId());
                    if (product != null) {
                        vo.setSupplierId(product.getSupplierId());
                    }
                }
            }
        }

        vo.setItems(itemMapper.selectList(new LambdaQueryWrapper<DefectItem>()
                .eq(DefectItem::getCaseId, id)
                .orderByAsc(DefectItem::getDefectLevel)
                .orderByAsc(DefectItem::getId)));
        vo.setTimeline(listTimeline(id));
        vo.setRectification(findRectificationVO(id));
        vo.setAllowedActions(stateMachine.allowedActions(
                BIZ_TYPE, caseEntity.getStatus(), SecurityUtils.getLoginUser().getRoles()));
        return vo;
    }

    public List<DefectApproval> listTimeline(Long caseId) {
        return approvalMapper.selectList(new LambdaQueryWrapper<DefectApproval>()
                .eq(DefectApproval::getCaseId, caseId)
                .orderByAsc(DefectApproval::getOperatedAt)
                .orderByAsc(DefectApproval::getId));
    }

    // ------------------------------------------------------------------
    // 质量评审
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "REVIEW", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void review(Long id, CaseReviewRequest request) {
        DefectCase caseEntity = getRequired(id);
        AuditContext.putBefore(writeJson(caseEntity));
        String disposition = request.getDisposition().trim().toUpperCase();
        if (!DISPOSITIONS.contains(disposition)) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法处置方式: " + disposition);
        }
        // A 类禁止让步接收（硬拦截，评审环节即拒绝）
        if ("CONCESSION".equals(disposition) && "A".equals(caseEntity.getMaxSeverity())) {
            throw new BizException(ResultCode.BIZ_JUDGE_VETO_FAIL,
                    "存在严重A类缺陷，禁止让步接收，必须退货/销毁等实质处置");
        }
        String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "REVIEW", currentRoles());
        caseEntity.setDisposition(disposition);
        caseEntity.setRootCause(request.getRootCause().trim());
        caseEntity.setOwnerId(SecurityUtils.getCurrentUserId());
        caseEntity.setStatus(next);
        updateWithLock(caseEntity);

        appendTimeline(id, "REVIEW", "SUBMIT",
                request.getComment(), AuditContext.getBefore(), snapshot(caseEntity), null);
        todoService.handle(BIZ_TYPE, id);
        todoService.createRoleTodo("QA_MANAGER", "APPROVAL", BIZ_TYPE, id,
                "不合格处置待审批：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
    }

    // ------------------------------------------------------------------
    // 主管审批（签名）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "APPROVE", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void approve(Long id, CaseApproveRequest request, String ip, String userAgent) {
        DefectCase caseEntity = getRequired(id);
        AuditContext.putBefore(writeJson(caseEntity));
        String action = request.getAction().trim().toUpperCase();
        if ("REJECT".equals(action)) {
            if (request.getComment() == null || request.getComment().isBlank()) {
                throw new BizException(ResultCode.PARAM_MISSING, "驳回必须填写意见");
            }
            String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "REJECT", currentRoles());
            caseEntity.setStatus(next);
            updateWithLock(caseEntity);
            appendTimeline(id, "APPROVAL", "REJECT", request.getComment().trim(),
                    AuditContext.getBefore(), snapshot(caseEntity), null);
            todoService.handle(BIZ_TYPE, id);
            todoService.createRoleTodo("REVIEWER", "DEFECT", BIZ_TYPE, id,
                    "评审被驳回需补充：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
            return;
        }
        if (!"PASS".equals(action)) {
            throw new BizException(ResultCode.PARAM_INVALID, "审批动作仅支持 PASS/REJECT");
        }
        authService.verifyPassword(SecurityUtils.getLoginUser(), request.getPassword());
        String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "APPROVE", currentRoles());
        caseEntity.setStatus(next);
        updateWithLock(caseEntity);

        Map<String, Object> signSnapshot = new LinkedHashMap<>();
        signSnapshot.put("caseId", id);
        signSnapshot.put("caseNo", caseEntity.getCaseNo());
        signSnapshot.put("disposition", caseEntity.getDisposition());
        signSnapshot.put("maxSeverity", caseEntity.getMaxSeverity());
        signSnapshot.put("rootCause", caseEntity.getRootCause());
        signatureService.record("DEFECT_APPROVAL", id, "不合格处置主管审批签名", signSnapshot, ip, userAgent);

        appendTimeline(id, "APPROVAL", "PASS", request.getComment(),
                AuditContext.getBefore(), snapshot(caseEntity), null);
        todoService.handle(BIZ_TYPE, id);
        todoService.createRoleTodo("QA_MANAGER", "DEFECT", BIZ_TYPE, id,
                "不合格待处置执行：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
    }

    // ------------------------------------------------------------------
    // 处置执行
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "EXECUTE", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void execute(Long id, CaseExecuteRequest request) {
        DefectCase caseEntity = getRequired(id);
        AuditContext.putBefore(writeJson(caseEntity));
        if ("RECTIFY".equals(caseEntity.getDisposition())) {
            SupplierRectification rect = findRectification(id);
            if (rect == null || !"PASSED".equals(rect.getStatus())) {
                throw new BizException(ResultCode.BIZ_STATE_INVALID,
                        "处置方式为供应商整改，须完成整改验证并复检通过后方可登记处置完成");
            }
        }
        String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "EXECUTE", currentRoles());
        caseEntity.setStatus(next);
        updateWithLock(caseEntity);
        appendTimeline(id, "DISPOSE", "EXECUTE", request.getComment().trim(),
                AuditContext.getBefore(), snapshot(caseEntity),
                request.getAttachmentIds() == null ? null : request.getAttachmentIds().trim());
        todoService.handle(BIZ_TYPE, id);
        todoService.createRoleTodo("QA_MANAGER", "DEFECT", BIZ_TYPE, id,
                "待复检闭环：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
    }

    // ------------------------------------------------------------------
    // 让步接收（仅 B/C，密码签名后直接闭环）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "CONCESSION", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void concession(Long id, CaseConcessionRequest request, String ip, String userAgent) {
        DefectCase caseEntity = getRequired(id);
        AuditContext.putBefore(writeJson(caseEntity));
        if (!"CONCESSION".equals(caseEntity.getDisposition())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "仅评审处置方式为「让步接收」的不合格单可走让步审批");
        }
        if ("A".equals(caseEntity.getMaxSeverity())) {
            throw new BizException(ResultCode.BIZ_JUDGE_VETO_FAIL, "严重A类缺陷禁止让步接收");
        }
        authService.verifyPassword(SecurityUtils.getLoginUser(), request.getPassword());
        String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "CONCESSION", currentRoles());
        caseEntity.setStatus(next);
        caseEntity.setClosedAt(LocalDateTime.now());
        updateWithLock(caseEntity);

        Map<String, Object> signSnapshot = new LinkedHashMap<>();
        signSnapshot.put("caseId", id);
        signSnapshot.put("caseNo", caseEntity.getCaseNo());
        signSnapshot.put("concessionReason", request.getComment());
        signSnapshot.put("maxSeverity", caseEntity.getMaxSeverity());
        signatureService.record("DEFECT_APPROVAL", id, "让步接收审批签名", signSnapshot, ip, userAgent);

        appendTimeline(id, "CLOSE", "PASS", "让步接收闭环：" + request.getComment().trim(),
                AuditContext.getBefore(), snapshot(caseEntity), null);
        todoService.handle(BIZ_TYPE, id);
        supplierQualityService.settleBySample(caseEntity.getSampleId());
    }

    // ------------------------------------------------------------------
    // 闭环 / 撤销
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "CLOSE", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void close(Long id, String comment) {
        DefectCase caseEntity = getRequired(id);
        AuditContext.putBefore(writeJson(caseEntity));
        String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "RECHECK_PASS", currentRoles());
        caseEntity.setStatus(next);
        caseEntity.setClosedAt(LocalDateTime.now());
        updateWithLock(caseEntity);
        appendTimeline(id, "CLOSE", "CLOSE",
                comment == null || comment.isBlank() ? "复检合格闭环" : comment.trim(),
                AuditContext.getBefore(), snapshot(caseEntity), null);
        todoService.handle(BIZ_TYPE, id);
        supplierQualityService.settleBySample(caseEntity.getSampleId());
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "不合格处置", action = "CANCEL", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void cancel(Long id, CaseCancelRequest request) {
        DefectCase caseEntity = getRequired(id);
        AuditContext.putBefore(writeJson(caseEntity));
        String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(), "CANCEL", currentRoles());
        caseEntity.setStatus(next);
        updateWithLock(caseEntity);
        appendTimeline(id, "REVIEW", "CANCEL", request.getComment().trim(),
                AuditContext.getBefore(), snapshot(caseEntity), null);
        todoService.handle(BIZ_TYPE, id);
    }

    // ------------------------------------------------------------------
    // 复检结论回写（由检验模块事件触发，同事务）
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRecheckConcluded(RecheckConcludedEvent event) {
        SupplierRectification rect = rectificationMapper.selectOne(new LambdaQueryWrapper<SupplierRectification>()
                .eq(SupplierRectification::getRecheckTaskId, event.childTaskId())
                .last("LIMIT 1"));
        DefectCase caseEntity = caseMapper.selectOne(new LambdaQueryWrapper<DefectCase>()
                .eq(DefectCase::getTaskId, event.parentTaskId())
                .last("LIMIT 1"));
        if (caseEntity == null && rect != null) {
            caseEntity = caseMapper.selectById(rect.getDefectCaseId());
        }
        if (caseEntity == null) {
            log.warn("复检结论回写找不到不合格单 child={} parent={}", event.childTaskId(), event.parentTaskId());
            return;
        }

        Long operatorId = SecurityUtils.getCurrentUserId();
        String operatorName = SecurityUtils.getCurrentUsername();
        if (event.qualified()) {
            if (rect != null) {
                rect.setStatus("PASSED");
                if (rect.getVerifiedAt() == null) {
                    rect.setVerifiedAt(LocalDateTime.now());
                }
                rectificationMapper.updateById(rect);
            }
            if ("PENDING_RECHECK".equals(caseEntity.getStatus())) {
                String before = writeJson(caseEntity);
                String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(),
                        "RECHECK_PASS", currentRoles());
                caseEntity.setStatus(next);
                caseEntity.setClosedAt(LocalDateTime.now());
                updateWithLock(caseEntity);
                appendTimelineSystem(caseEntity.getId(), "RECHECK", "PASS",
                        "复检合格，闭环（复检任务id=" + event.childTaskId() + "）",
                        before, snapshot(caseEntity), operatorId, operatorName);
                todoService.handle(BIZ_TYPE, caseEntity.getId());
                supplierQualityService.settleBySample(caseEntity.getSampleId());
            }
        } else {
            if (rect != null) {
                rect.setStatus("FAILED");
                rectificationMapper.updateById(rect);
            }
            if ("PENDING_RECHECK".equals(caseEntity.getStatus())) {
                String before = writeJson(caseEntity);
                String next = stateMachine.nextState(BIZ_TYPE, caseEntity.getStatus(),
                        "RECHECK_FAIL", currentRoles());
                caseEntity.setStatus(next);
                updateWithLock(caseEntity);
                appendTimelineSystem(caseEntity.getId(), "RECHECK", "REJECT",
                        "复检仍不合格，退回重新处置（复检任务id=" + event.childTaskId() + "）",
                        before, snapshot(caseEntity), operatorId, operatorName);
                todoService.createRoleTodo("QA_MANAGER", "DEFECT", BIZ_TYPE, caseEntity.getId(),
                        "复检不合格重新处置：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
            }
        }
    }

    // ------------------------------------------------------------------
    // 报告签发联动（同事务自动建档）
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onReportIssued(ReportIssuedEvent event) {
        InspectionReport report = reportMapper.selectById(event.reportId());
        if (report != null) {
            createFromIssuedReport(report);
        }
    }

    // ------------------------------------------------------------------
    // 供整改模块调用的包级能力
    // ------------------------------------------------------------------

    /** 整改流程追加时间线（外部模块调用，操作人取当前登录用户）。 */
    public void appendExternalTimeline(Long caseId, String node, String action, String comment,
                                       Object before, Object after) {
        appendTimeline(caseId, node, action, comment, before, after, null);
    }

    /** 快照归一化：Map 原样使用；审计 JSON 字符串反序列化为 Map；失败降级 null。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toSnapshotMap(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (snapshot instanceof String json && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("时间线快照反序列化失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /** 整改验证失败：若不合格单已到待复检，则退回处置中。 */
    @Transactional(rollbackFor = Exception.class)
    public void backToProcessingAfterVerifyFailed(Long caseId) {
        DefectCase caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            return;
        }
        if ("PENDING_RECHECK".equals(caseEntity.getStatus())) {
            String before = writeJson(caseEntity);
            String next = stateMachine.nextState(BIZ_TYPE, "PENDING_RECHECK",
                    "RECHECK_FAIL", currentRoles());
            caseEntity.setStatus(next);
            updateWithLock(caseEntity);
            appendTimeline(caseId, "RECTIFY", "REJECT", "整改验证失败，退回处置中",
                    before, snapshot(caseEntity), null);
        }
    }

    /**
     * 整改验证通过并自动发起复检时，将不合格单由处置中转待复检
     * （等效登记处置完成、送检复检），复检结论再由事件闭环。
     * 非处置中状态幂等返回，避免重复转态。
     */
    public void enterPendingRecheckFromRectification(Long caseId, String rectifyNo, Long recheckTaskId) {
        DefectCase caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "不合格处置单不存在");
        }
        if (!"PROCESSING".equals(caseEntity.getStatus())) {
            return;
        }
        String before = writeJson(caseEntity);
        String next = stateMachine.nextState(BIZ_TYPE, "PROCESSING", "EXECUTE", currentRoles());
        caseEntity.setStatus(next);
        updateWithLock(caseEntity);
        appendTimeline(caseId, "RECHECK", "SUBMIT",
                "整改单 " + rectifyNo + " 验证通过，自动送检复检（复检任务id=" + recheckTaskId + "）",
                before, snapshot(caseEntity), null);
        todoService.createRoleTodo("QA_MANAGER", "DEFECT", BIZ_TYPE, caseEntity.getId(),
                "待复检闭环：" + caseEntity.getCaseNo(), caseEntity.getCaseNo(), null);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private DefectCase getRequired(Long id) {
        DefectCase caseEntity = caseMapper.selectById(id);
        if (caseEntity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "不合格处置单不存在");
        }
        return caseEntity;
    }

    private void updateWithLock(DefectCase caseEntity) {
        int rows = caseMapper.updateById(caseEntity);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    private SupplierRectification findRectification(Long caseId) {
        return rectificationMapper.selectOne(new LambdaQueryWrapper<SupplierRectification>()
                .eq(SupplierRectification::getDefectCaseId, caseId)
                .orderByDesc(SupplierRectification::getId)
                .last("LIMIT 1"));
    }

    private RectificationVO findRectificationVO(Long caseId) {
        SupplierRectification rect = findRectification(caseId);
        if (rect == null) {
            return null;
        }
        RectificationVO vo = new RectificationVO();
        vo.setRect(rect);
        Supplier supplier = supplierMapper.selectById(rect.getSupplierId());
        if (supplier != null) {
            vo.setSupplierName(supplier.getSupplierName());
        }
        DefectCase linked = caseMapper.selectById(rect.getDefectCaseId());
        if (linked != null) {
            vo.setCaseNo(linked.getCaseNo());
        }
        if (rect.getRecheckTaskId() != null) {
            InspectionTask recheckTask = taskMapper.selectById(rect.getRecheckTaskId());
            if (recheckTask != null) {
                vo.setRecheckTaskNo(recheckTask.getTaskNo());
            }
        }
        if (rect.getVerifierId() != null) {
            com.qms.modules.system.entity.SysUser verifier =
                    sysUserMapper.selectById(rect.getVerifierId());
            if (verifier != null) {
                vo.setVerifierName(verifier.getRealName());
            }
        }
        return vo;
    }

    private void appendTimeline(Long caseId, String node, String action, String comment,
                                Object before, Object after,
                                String attachmentIds) {
        appendTimelineSystem(caseId, node, action, comment, before, after,
                SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUsername(), attachmentIds);
    }

    private void appendTimelineSystem(Long caseId, String node, String action, String comment,
                                      Object before, Object after,
                                      Long operatorId, String operatorName) {
        appendTimelineSystem(caseId, node, action, comment, before, after,
                operatorId, operatorName, null);
    }

    private void appendTimelineSystem(Long caseId, String node, String action, String comment,
                                      Object before, Object after,
                                      Long operatorId, String operatorName, String attachmentIds) {
        DefectApproval approval = new DefectApproval();
        approval.setCaseId(caseId);
        approval.setNode(node);
        approval.setAction(action);
        approval.setComment(comment);
        approval.setBeforeSnapshot(toSnapshotMap(before));
        approval.setAfterSnapshot(toSnapshotMap(after));
        approval.setAttachmentIds(attachmentIds);
        approval.setOperatorId(operatorId);
        approval.setOperatorName(operatorName);
        approval.setOperatedAt(LocalDateTime.now());
        approval.setTenantId(SecurityUtils.getTenantId());
        approvalMapper.insert(approval);
    }

    private Map<String, Object> snapshot(DefectCase caseEntity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("caseNo", caseEntity.getCaseNo());
        map.put("status", caseEntity.getStatus());
        map.put("maxSeverity", caseEntity.getMaxSeverity());
        map.put("disposition", caseEntity.getDisposition());
        map.put("rootCause", caseEntity.getRootCause());
        map.put("closedAt", caseEntity.getClosedAt());
        return map;
    }

    private String buildFailDesc(InspectionResult result, StandardItem item) {
        String measured = switch (result.getResultType()) {
            case "QUALITATIVE" -> "定性判定：不符合";
            case "DOCUMENT" -> result.getDocAttachmentId() == null ? "凭证附件缺失" : "凭证不合格";
            default -> {
                String v = result.getQuantitativeValue() == null
                        ? "未录入" : result.getQuantitativeValue().stripTrailingZeros().toPlainString();
                String unit = result.getUnit() == null ? "" : " " + result.getUnit();
                String standard = item == null ? "" : standardBrief(item);
                yield "实测 " + v + unit + (standard.isBlank() ? "" : "；标准 " + standard);
            }
        };
        return result.getItemName() + "：" + measured;
    }

    private String standardBrief(StandardItem item) {
        if (item.getMinValue() != null || item.getMaxValue() != null) {
            return (item.getMinValue() == null ? "−∞" : item.getMinValue())
                    + "~" + (item.getMaxValue() == null ? "+∞" : item.getMaxValue())
                    + (item.getUnit() == null ? "" : " " + item.getUnit());
        }
        if (item.getJudgeConfig() != null && item.getJudgeConfig().get("operator") != null) {
            Map<String, Object> cfg = item.getJudgeConfig();
            return cfg.get("operator") + " " + cfg.get("value")
                    + (cfg.get("value2") == null ? "" : "~" + cfg.get("value2"));
        }
        return "";
    }

    private int severityRank(String level) {
        return "A".equals(level) ? 0 : "B".equals(level) ? 1 : 2;
    }

    private Set<String> currentRoles() {
        return SecurityUtils.getLoginUser().getRoles();
    }

    private CaseListVO toListVO(DefectCase caseEntity) {
        CaseListVO vo = new CaseListVO();
        vo.setCaseObj(caseEntity);
        if (caseEntity.getReportId() != null) {
            InspectionReport report = reportMapper.selectById(caseEntity.getReportId());
            if (report != null) {
                vo.setReportNo(report.getReportNo());
                vo.setConclusion(report.getConclusion());
                vo.setAFailCount(report.getAFailCount());
                vo.setBFailCount(report.getBFailCount());
                vo.setCFailCount(report.getCFailCount());
                vo.setIssuedAt(report.getIssuedAt());
            }
        }
        if (caseEntity.getTaskId() != null) {
            InspectionTask task = taskMapper.selectById(caseEntity.getTaskId());
            if (task != null) {
                vo.setTaskNo(task.getTaskNo());
                Sample sample = sampleMapper.selectById(task.getSampleId());
                if (sample != null) {
                    vo.setSampleNo(sample.getSampleNo());
                    vo.setBatchNo(sample.getBatchNo());
                    ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
                    if (sku != null) {
                        vo.setSpec(sku.getSpec());
                        Product product = productMapper.selectById(sku.getProductId());
                        if (product != null) {
                            vo.setProductName(product.getProductName());
                            Supplier supplier = supplierMapper.selectById(product.getSupplierId());
                            if (supplier != null) {
                                vo.setSupplierName(supplier.getSupplierName());
                            }
                        }
                    }
                }
            }
        }
        Long cnt = itemMapper.selectCount(new LambdaQueryWrapper<DefectItem>()
                .eq(DefectItem::getCaseId, caseEntity.getId()));
        vo.setItemCount(cnt == null ? 0 : cnt.intValue());
        return vo;
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
