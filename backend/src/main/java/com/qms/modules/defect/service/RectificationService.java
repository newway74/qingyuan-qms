package com.qms.modules.defect.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.framework.biz.BizNoGenerator;
import com.qms.modules.defect.dto.RectificationCreateRequest;
import com.qms.modules.defect.dto.RectificationReplyRequest;
import com.qms.modules.defect.dto.RectificationVerifyRequest;
import com.qms.modules.defect.entity.DefectCase;
import com.qms.modules.defect.entity.SupplierRectification;
import com.qms.modules.defect.mapper.DefectCaseMapper;
import com.qms.modules.defect.mapper.SupplierRectificationMapper;
import com.qms.modules.defect.vo.RectificationVO;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.service.InspectionTaskService;
import com.qms.modules.inspection.service.SignatureService;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import com.qms.modules.system.service.AuthService;
import com.qms.modules.todo.service.TodoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商整改单服务：签发 → 供应商回复纠正措施 → 质量主管验证（签名）
 * → 验证通过生成关联复检子任务（recheck_parent_id）→ 复检结论由事件回写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RectificationService {

    public static final String BIZ_TYPE = "qc_supplier_rectification";

    private final SupplierRectificationMapper rectificationMapper;
    private final DefectCaseMapper caseMapper;
    private final SupplierMapper supplierMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final SampleMapper sampleMapper;
    private final SysUserMapper sysUserMapper;
    private final BizNoGenerator bizNoGenerator;
    private final AuthService authService;
    private final SignatureService signatureService;
    private final InspectionTaskService inspectionTaskService;
    private final DefectCaseService defectCaseService;
    private final TodoService todoService;
    private final ObjectMapper objectMapper;

    public PageResult<RectificationVO> page(PageRequest request, String rectifyNo, String status) {
        LambdaQueryWrapper<SupplierRectification> wrapper =
                new LambdaQueryWrapper<SupplierRectification>()
                        .like(rectifyNo != null && !rectifyNo.isBlank(),
                                SupplierRectification::getRectifyNo, rectifyNo)
                        .eq(status != null && !status.isBlank(),
                                SupplierRectification::getStatus, status)
                        .orderByDesc(SupplierRectification::getId);
        Page<SupplierRectification> page = rectificationMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toVO).toList());
    }

    public RectificationVO detail(Long id) {
        return toVO(getRequired(id));
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商整改", action = "ISSUE", bizType = BIZ_TYPE)
    public Long create(RectificationCreateRequest request) {
        DefectCase caseEntity = caseMapper.selectById(request.getDefectCaseId());
        if (caseEntity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "不合格处置单不存在");
        }
        if (List.of("CLOSED", "CANCELLED").contains(caseEntity.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "不合格单已结束，不能签发整改单");
        }
        if (!"RECTIFY".equals(caseEntity.getDisposition())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID,
                    "仅评审处置方式为「供应商整改」的不合格单可签发整改单");
        }
        Long exists = rectificationMapper.selectCount(new LambdaQueryWrapper<SupplierRectification>()
                .eq(SupplierRectification::getDefectCaseId, caseEntity.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "该不合格单已签发整改单");
        }
        Long supplierId = resolveSupplierId(caseEntity);

        SupplierRectification rect = new SupplierRectification();
        rect.setRectifyNo(bizNoGenerator.next("ZG"));
        rect.setDefectCaseId(caseEntity.getId());
        rect.setSupplierId(supplierId);
        rect.setIssueDesc(request.getIssueDesc().trim());
        rect.setPlanFinishDate(request.getPlanFinishDate());
        rect.setStatus("ISSUED");
        rectificationMapper.insert(rect);

        defectCaseService.appendExternalTimeline(caseEntity.getId(), "RECTIFY", "SUBMIT",
                "签发供应商整改单 " + rect.getRectifyNo(), null, rectSnapshot(rect));
        todoService.createRoleTodo("QA_MANAGER", "RECT", BIZ_TYPE, rect.getId(),
                "整改单待验证：" + rect.getRectifyNo(), rect.getRectifyNo(), null);
        log.info("整改单签发 rectifyNo={} case={}", rect.getRectifyNo(), caseEntity.getCaseNo());
        return rect.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商整改", action = "REPLY", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void reply(Long id, RectificationReplyRequest request) {
        SupplierRectification rect = getRequired(id);
        AuditContext.putBefore(writeJson(rect));
        if (!"ISSUED".equals(rect.getStatus()) && !"FAILED".equals(rect.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅已签发/验证失败的整改单可回复");
        }
        if (request.getPlanFinishDate() == null && "ISSUED".equals(rect.getStatus())) {
            throw new BizException(ResultCode.PARAM_MISSING, "计划完成日期不能为空");
        }
        if (request.getPlanFinishDate() != null
                && request.getPlanFinishDate().isBefore(LocalDate.now())) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "计划完成日期不能早于今天");
        }
        rect.setCorrectiveAction(request.getCorrectiveAction().trim());
        // 验证失败后重新回复未传计划完成日时，沿用原计划完成日
        if (request.getPlanFinishDate() != null) {
            rect.setPlanFinishDate(request.getPlanFinishDate());
        }
        rect.setActualFinishDate(request.getActualFinishDate());
        rect.setStatus("REPLIED");
        rectificationMapper.updateById(rect);
        defectCaseService.appendExternalTimeline(rect.getDefectCaseId(), "RECTIFY", "SUBMIT",
                "供应商提交整改回复", AuditContext.getBefore(), rectSnapshot(rect));
        todoService.handle(BIZ_TYPE, id);
        todoService.createRoleTodo("QA_MANAGER", "RECT", BIZ_TYPE, rect.getId(),
                "整改回复待验证：" + rect.getRectifyNo(), rect.getRectifyNo(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商整改", action = "VERIFY", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void verify(Long id, RectificationVerifyRequest request, String ip, String userAgent) {
        SupplierRectification rect = getRequired(id);
        AuditContext.putBefore(writeJson(rect));
        if (!"REPLIED".equals(rect.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅已回复的整改单可验证");
        }
        authService.verifyPassword(SecurityUtils.getLoginUser(), request.getPassword());
        String result = request.getResult().trim().toUpperCase();
        DefectCase caseEntity = caseMapper.selectById(rect.getDefectCaseId());

        if ("FAIL".equals(result)) {
            rect.setStatus("FAILED");
            rect.setVerifierId(SecurityUtils.getCurrentUserId());
            rect.setVerifiedAt(LocalDateTime.now());
            rectificationMapper.updateById(rect);
            // 验证不通过：不合格单退回处置中（若当前在处置中则仅记录）
            defectCaseService.backToProcessingAfterVerifyFailed(caseEntity.getId());
            defectCaseService.appendExternalTimeline(caseEntity.getId(), "RECTIFY", "REJECT",
                    "整改验证不通过：" + (request.getComment() == null ? "" : request.getComment().trim()),
                    AuditContext.getBefore(), rectSnapshot(rect));
            todoService.handle(BIZ_TYPE, id);
            todoService.createRoleTodo("QA_MANAGER", "RECT", BIZ_TYPE, rect.getId(),
                    "整改验证失败需重新回复：" + rect.getRectifyNo(), rect.getRectifyNo(), null);
            return;
        }
        if (!"PASS".equals(result)) {
            throw new BizException(ResultCode.PARAM_INVALID, "验证结论仅支持 PASS/FAIL");
        }
        if (caseEntity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "关联不合格单不存在");
        }
        InspectionTask parent = inspectionTaskService.getRequired(caseEntity.getTaskId());
        String reason = "整改单 " + rect.getRectifyNo() + " 验证通过，发起复检"
                + (request.getComment() == null ? "" : "：" + request.getComment().trim());
        // 原任务 JUDGED→RECHECKING，并创建复检子任务（复制模板快照/分配/原结果）
        InspectionTask child = inspectionTaskService.startRecheck(parent, reason);

        rect.setStatus("VERIFYING");
        rect.setRecheckTaskId(child.getId());
        rect.setVerifierId(SecurityUtils.getCurrentUserId());
        rect.setVerifiedAt(LocalDateTime.now());
        int rows = rectificationMapper.updateById(rect);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }

        Map<String, Object> signSnapshot = new LinkedHashMap<>();
        signSnapshot.put("rectifyNo", rect.getRectifyNo());
        signSnapshot.put("recheckTaskId", child.getId());
        signSnapshot.put("recheckTaskNo", child.getTaskNo());
        signatureService.record("DEFECT_APPROVAL", caseEntity.getId(),
                "整改验证通过发起复检签名", signSnapshot, ip, userAgent);

        // 不合格单：处置中 -> 待复检（同时写时间线），复检结论由事件自动闭环
        defectCaseService.enterPendingRecheckFromRectification(
                caseEntity.getId(), rect.getRectifyNo(), child.getId());
        todoService.handle(BIZ_TYPE, id);
        log.info("整改验证通过 rectifyNo={} recheckTask={}", rect.getRectifyNo(), child.getTaskNo());
    }

    // ------------------------------------------------------------------

    private SupplierRectification getRequired(Long id) {
        SupplierRectification rect = rectificationMapper.selectById(id);
        if (rect == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "整改单不存在");
        }
        return rect;
    }

    private Long resolveSupplierId(DefectCase caseEntity) {
        InspectionTask task = inspectionTaskService.getRequired(caseEntity.getTaskId());
        Sample sample = sampleMapper.selectById(task.getSampleId());
        if (sample == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "任务关联样品不存在");
        }
        ProductSku sku = productSkuMapper.selectById(sample.getSkuId());
        if (sku == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "样品关联SKU不存在");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || product.getSupplierId() == null) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "产品未关联供应商，无法签发整改单");
        }
        return product.getSupplierId();
    }

    private Map<String, Object> rectSnapshot(SupplierRectification rect) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rectifyNo", rect.getRectifyNo());
        map.put("status", rect.getStatus());
        map.put("planFinishDate", rect.getPlanFinishDate());
        map.put("actualFinishDate", rect.getActualFinishDate());
        map.put("recheckTaskId", rect.getRecheckTaskId());
        return map;
    }

    private RectificationVO toVO(SupplierRectification rect) {
        RectificationVO vo = new RectificationVO();
        vo.setRect(rect);
        Supplier supplier = supplierMapper.selectById(rect.getSupplierId());
        if (supplier != null) {
            vo.setSupplierName(supplier.getSupplierName());
        }
        DefectCase caseEntity = caseMapper.selectById(rect.getDefectCaseId());
        if (caseEntity != null) {
            vo.setCaseNo(caseEntity.getCaseNo());
        }
        if (rect.getRecheckTaskId() != null) {
            InspectionTask child = inspectionTaskService.getRequired(rect.getRecheckTaskId());
            vo.setRecheckTaskNo(child.getTaskNo());
        }
        if (rect.getVerifierId() != null) {
            SysUser verifier = sysUserMapper.selectById(rect.getVerifierId());
            if (verifier != null) {
                vo.setVerifierName(verifier.getRealName());
            }
        }
        return vo;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
