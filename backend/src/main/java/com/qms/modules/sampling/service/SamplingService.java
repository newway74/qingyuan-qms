package com.qms.modules.sampling.service;

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
import com.qms.framework.statemachine.StateMachineEngine;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.process.service.ProcessDefService;
import com.qms.modules.process.vo.ProcessDefDetailVO;
import com.qms.modules.sampling.dto.SamplingCancelRequest;
import com.qms.modules.sampling.dto.SamplingUpsertRequest;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.entity.Sampling;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.sampling.mapper.SamplingMapper;
import com.qms.modules.sampling.vo.SamplingVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * 抽样单服务。
 * 不变量：
 * 1) CY 编号创建即生成（Redis 自增 + 唯一索引兜底）；
 * 2) DRAFT 可编辑；提交强校验批号/生产日期/保质期至/数量，并快照产品保质期天数与差异、流程版本；
 * 3) 撤销必须填写原因并留痕；状态流转必须经状态机；乐观锁防并发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SamplingService {

    public static final String BIZ_TYPE = "qc_sampling";

    private static final Set<String> SOURCES = Set.of(
            "NEW_ADMISSION", "INCOMING_BATCH", "PERIODIC", "COMPLAINT", "FLYING");

    private final SamplingMapper samplingMapper;
    private final SampleMapper sampleMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final BizNoGenerator bizNoGenerator;
    private final StateMachineEngine stateMachine;
    private final ProcessDefService processDefService;
    private final SamplingViews views;
    private final ObjectMapper objectMapper;
    private final com.qms.modules.todo.service.TodoService todoService;

    // ---------------- 查询 ----------------

    public PageResult<SamplingVO> page(PageRequest request, String samplingNo, String source,
                                       String status, Long skuId, String batchNo, String keyword) {
        LambdaQueryWrapper<Sampling> wrapper = new LambdaQueryWrapper<Sampling>()
                .like(samplingNo != null && !samplingNo.isBlank(), Sampling::getSamplingNo, samplingNo)
                .eq(source != null && !source.isBlank(), Sampling::getSource, source)
                .eq(status != null && !status.isBlank(), Sampling::getStatus, status)
                .eq(skuId != null, Sampling::getSkuId, skuId)
                .like(batchNo != null && !batchNo.isBlank(), Sampling::getBatchNo, batchNo)
                .orderByDesc(Sampling::getId);
        if (keyword != null && !keyword.isBlank()) {
            // 关键词匹配抽样单号/批号（品名搜索由列表端通过 skuId 精确筛选，避免跨库模糊）
            wrapper.and(w -> w.like(Sampling::getSamplingNo, keyword)
                    .or().like(Sampling::getBatchNo, keyword));
        }
        Page<Sampling> page = samplingMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<SamplingVO> records = page.getRecords().stream()
                .map(s -> views.toSamplingVO(s, null))
                .toList();
        return PageResult.of(page, records);
    }

    public SamplingVO detail(Long id) {
        Sampling sampling = getRequired(id);
        List<Sample> samples = sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSamplingId, id)
                .orderByAsc(Sample::getSampleType)
                .orderByAsc(Sample::getId));
        return views.toSamplingVO(sampling, samples);
    }

    // ---------------- 草稿 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "抽样单", action = "CREATE", bizType = BIZ_TYPE)
    public Long create(SamplingUpsertRequest request) {
        validateCommon(request);
        Sampling sampling = new Sampling();
        apply(sampling, request);
        sampling.setSamplingNo(bizNoGenerator.next("CY"));
        sampling.setStatus("DRAFT");
        sampling.setSamplerId(SecurityUtils.getCurrentUserId());
        sampling.setSampledAt(request.getSampledAt() == null ? LocalDateTime.now() : request.getSampledAt());
        samplingMapper.insert(sampling);
        return sampling.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "抽样单", action = "UPDATE", bizType = BIZ_TYPE, bizIdExpr = "#request.id")
    public void updateDraft(SamplingUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "id不能为空");
        }
        Sampling existing = getRequired(request.getId());
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅草稿状态抽样单可编辑");
        }
        putBefore(existing);
        validateCommon(request);
        apply(existing, request);
        int rows = samplingMapper.updateById(existing);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    // ---------------- 提交/撤销 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "抽样单", action = "SUBMIT", bizType = BIZ_TYPE, bizIdExpr = "#id")
    public void submit(Long id) {
        Sampling sampling = getRequired(id);
        putBefore(sampling);
        String next = stateMachine.nextState(BIZ_TYPE, sampling.getStatus(), "SUBMIT", currentRoles());

        // 提交强校验
        ProductSku sku = productSkuMapper.selectById(sampling.getSkuId());
        if (sku == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "关联SKU不存在");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "关联产品不存在");
        }
        if (sampling.getBatchNo() == null || sampling.getBatchNo().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "批号不能为空（需核对包装喷码）");
        }
        LocalDate production = sampling.getProductionDate();
        LocalDate expiry = sampling.getExpiryDate();
        if (production == null || expiry == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "生产日期与保质期至不能为空");
        }
        if (production.isAfter(LocalDate.now())) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "生产日期不能晚于当天");
        }
        if (expiry.isBefore(production)) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "保质期至不能早于生产日期");
        }
        if (sampling.getSampleQuantity() == null
                || sampling.getSampleQuantity().signum() <= 0) {
            throw new BizException(ResultCode.PARAM_MISSING, "抽样数量必须大于0");
        }

        // 保质期快照与差异留痕（差异不阻断，供收样核对与审计追溯）
        sampling.setShelfLifeDaysSnapshot(product.getShelfLifeDays());
        if (product.getShelfLifeDays() != null && product.getShelfLifeDays() > 0) {
            LocalDate expectedExpiry = production.plusDays(product.getShelfLifeDays());
            sampling.setExpiryDiffDays((int) ChronoUnit.DAYS.between(expectedExpiry, expiry));
        }
        if (sampling.getStorageCondition() == null || sampling.getStorageCondition().isBlank()) {
            sampling.setStorageCondition(product.getStorageCondition());
        }

        // 流程版本快照：提交时锁定当前 PUBLISHED 流程
        ProcessDefDetailVO effectiveProcess = processDefService.effective();
        sampling.setProcessDefId(effectiveProcess.getProcessDef().getId());

        sampling.setStatus(next);
        int rows = samplingMapper.updateById(sampling);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }

        // 提交后挂抽样收样岗角色待办（收样登记时核销）
        todoService.createRoleTodo("SAMPLER", "RECEIVE", BIZ_TYPE, sampling.getId(),
                "待收样登记：" + sampling.getSamplingNo(), sampling.getSamplingNo(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "抽样单", action = "CANCEL", bizType = BIZ_TYPE, bizIdExpr = "#request.id")
    public void cancel(SamplingCancelRequest request) {
        Sampling sampling = getRequired(request.getId());
        putBefore(sampling);
        if ("RECEIVED".equals(sampling.getStatus()) || "ARCHIVED".equals(sampling.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "已收样/已归档抽样单不可撤销");
        }
        String next = stateMachine.nextState(BIZ_TYPE, sampling.getStatus(), "CANCEL", currentRoles());
        sampling.setStatus(next);
        sampling.setCancelReason(request.getReason().trim());
        int rows = samplingMapper.updateById(sampling);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
        // 撤销时核销未处理的收样待办
        todoService.handle(BIZ_TYPE, sampling.getId());
    }

    // ------------------------------------------------------------------

    private void apply(Sampling sampling, SamplingUpsertRequest request) {
        sampling.setSource(request.getSource().trim());
        sampling.setSkuId(request.getSkuId());
        sampling.setBatchNo(request.getBatchNo().trim());
        sampling.setProductionDate(request.getProductionDate());
        sampling.setExpiryDate(request.getExpiryDate());
        sampling.setStorageCondition(trim(request.getStorageCondition()));
        sampling.setSampleQuantity(request.getSampleQuantity());
        sampling.setQuantityUnit(request.getQuantityUnit() == null || request.getQuantityUnit().isBlank()
                ? "件" : request.getQuantityUnit().trim());
        sampling.setSamplingLocation(trim(request.getSamplingLocation()));
        sampling.setSampledAt(request.getSampledAt());
        sampling.setRemark(trim(request.getRemark()));
    }

    private void validateCommon(SamplingUpsertRequest request) {
        if (!SOURCES.contains(request.getSource().trim())) {
            throw new BizException(ResultCode.PARAM_INVALID, "非法抽样来源");
        }
        if (productSkuMapper.selectById(request.getSkuId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "所选SKU不存在");
        }
        if (request.getProductionDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getProductionDate())) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "保质期至不能早于生产日期");
        }
    }

    public Sampling getRequired(Long id) {
        Sampling sampling = samplingMapper.selectById(id);
        if (sampling == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "抽样单不存在");
        }
        return sampling;
    }

    private Set<String> currentRoles() {
        return SecurityUtils.getLoginUser().getRoles();
    }

    private void putBefore(Object entity) {
        try {
            AuditContext.putBefore(objectMapper.writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("审计before快照写入失败: {}", e.getMessage());
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
