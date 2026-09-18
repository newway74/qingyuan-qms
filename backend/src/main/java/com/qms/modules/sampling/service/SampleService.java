package com.qms.modules.sampling.service;

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
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.inspection.service.InspectionTaskService;
import com.qms.modules.sampling.dto.SampleDisposeRequest;
import com.qms.modules.sampling.dto.SampleReceiveRequest;
import com.qms.modules.sampling.dto.SampleRetainRequest;
import com.qms.modules.sampling.entity.Batch;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.entity.Sampling;
import com.qms.modules.sampling.mapper.BatchMapper;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.sampling.mapper.SamplingMapper;
import com.qms.modules.sampling.vo.SampleVO;
import com.qms.modules.sampling.vo.SamplingVO;
import com.qms.modules.todo.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 样品服务：收样登记（喷码强校验、多样品生成、自动建任务、批次台账）、留样/处置、搜索扫码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleService {

    public static final String BIZ_TYPE = "qc_sample";
    private static final long NEAR_EXPIRY_DAYS = 30;

    private final SampleMapper sampleMapper;
    private final SamplingMapper samplingMapper;
    private final BatchMapper batchMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionTaskService inspectionTaskService;
    private final SamplingService samplingService;
    private final SamplingViews views;
    private final BizNoGenerator bizNoGenerator;
    private final StateMachineEngine stateMachine;
    private final ObjectMapper objectMapper;
    private final TodoService todoService;

    // ------------------------------------------------------------------
    // 收样登记
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "样品收样", action = "RECEIVE", bizType = "qc_sampling", bizIdExpr = "#request.samplingId")
    public SamplingVO receive(SampleReceiveRequest request) {
        Sampling sampling = samplingService.getRequired(request.getSamplingId());
        if (!"PENDING_RECEIVE".equals(sampling.getStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "仅待收样的抽样单可收样登记");
        }
        Long existing = sampleMapper.selectCount(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSamplingId, sampling.getId()));
        if (existing != null && existing > 0) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "该抽样单已完成收样，不能重复登记");
        }

        // 喷码核对强校验：不一致必须说明差异
        if (!"MATCH".equals(request.getPackageBatchCheck())
                && !"MISMATCH".equals(request.getPackageBatchCheck())) {
            throw new BizException(ResultCode.PARAM_INVALID, "喷码核对结果仅支持 MATCH/MISMATCH");
        }
        if ("MISMATCH".equals(request.getPackageBatchCheck())
                && (request.getPackageBatchNote() == null || request.getPackageBatchNote().isBlank())) {
            throw new BizException(ResultCode.PARAM_MISSING,
                    "喷码批号/生产日期不一致时必须填写差异说明");
        }

        LocalDate productionDate = request.getProductionDate() != null
                ? request.getProductionDate() : sampling.getProductionDate();
        LocalDate expiryDate = request.getExpiryDate() != null
                ? request.getExpiryDate() : sampling.getExpiryDate();
        if (productionDate == null || expiryDate == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "生产日期与保质期至不能为空");
        }
        if (expiryDate.isBefore(productionDate)) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "保质期至不能早于生产日期");
        }

        // 抽样单状态流转（RECEIVE）
        String samplingNext = stateMachine.nextState("qc_sampling",
                sampling.getStatus(), "RECEIVE", currentRoles());
        AuditContext.putBefore(writeJson(sampling));
        sampling.setStatus(samplingNext);
        samplingMapper.updateById(sampling);
        // 收样完成，核销抽样单的收样待办
        todoService.handle("qc_sampling", sampling.getId());

        LocalDateTime now = LocalDateTime.now();
        Long receiverId = SecurityUtils.getCurrentUserId();
        int inspectionCount = request.getInspectionCount() == null ? 1 : request.getInspectionCount();

        // 1) 检验样：IN_INSPECTION，逐样自动建检验任务（模板/流程版本快照）
        for (int i = 0; i < inspectionCount; i++) {
            Sample inspectionSample = newSample(sampling, "INSPECTION", productionDate, expiryDate,
                    request, now, receiverId);
            inspectionSample.setStatus("IN_INSPECTION");
            sampleMapper.insert(inspectionSample);
            inspectionTaskService.createTaskForSample(inspectionSample, sampling.getProcessDefId(),
                    1, null, null);
        }

        // 2) 留样：提供留样位+到期日则直接 RETAINING，否则待留样登记
        if (Boolean.TRUE.equals(request.getRetainFlag())) {
            Sample retain = newSample(sampling, "RETAIN", productionDate, expiryDate,
                    request, now, receiverId);
            retain.setRetainFlag(1);
            boolean directRetain = request.getRetainLocation() != null && !request.getRetainLocation().isBlank()
                    && request.getRetainUntil() != null;
            if (directRetain) {
                String next = stateMachine.nextState(BIZ_TYPE, "PENDING_RECEIVE", "RETAIN", currentRoles());
                if (!"RETAINING".equals(next)) {
                    throw new BizException(ResultCode.BIZ_STATE_INVALID, "留样状态流转异常");
                }
                retain.setStatus("RETAINING");
                retain.setRetainLocation(request.getRetainLocation().trim());
                retain.setRetainUntil(request.getRetainUntil());
            } else {
                retain.setStatus("PENDING_RECEIVE");
            }
            sampleMapper.insert(retain);
        }

        // 3) 备样：待收样（后续转留样或处置）
        if (Boolean.TRUE.equals(request.getBackupFlag())) {
            Sample backup = newSample(sampling, "BACKUP", productionDate, expiryDate,
                    request, now, receiverId);
            backup.setStatus("PENDING_RECEIVE");
            sampleMapper.insert(backup);
        }

        // 4) 批次台账 upsert（同 SKU+批号唯一）
        upsertBatch(sampling, productionDate, expiryDate);

        return samplingService.detail(sampling.getId());
    }

    private Sample newSample(Sampling sampling, String type, LocalDate productionDate,
                             LocalDate expiryDate, SampleReceiveRequest request,
                             LocalDateTime now, Long receiverId) {
        Sample sample = new Sample();
        sample.setSampleNo(bizNoGenerator.next("YP"));
        sample.setSamplingId(sampling.getId());
        sample.setSkuId(sampling.getSkuId());
        sample.setSampleType(type);
        sample.setBatchNo(sampling.getBatchNo());
        sample.setProductionDate(productionDate);
        sample.setExpiryDate(expiryDate);
        sample.setPackageBatchCheck(request.getPackageBatchCheck());
        sample.setPackageBatchNote(request.getPackageBatchNote() == null ? null
                : request.getPackageBatchNote().trim());
        sample.setReceivedAt(now);
        sample.setReceiverId(receiverId);
        sample.setRetainFlag(0);
        return sample;
    }

    private void upsertBatch(Sampling sampling, LocalDate productionDate, LocalDate expiryDate) {
        Batch existing = batchMapper.selectOne(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getTenantId, SecurityUtils.getTenantId())
                .eq(Batch::getSkuId, sampling.getSkuId())
                .eq(Batch::getBatchNo, sampling.getBatchNo())
                .last("LIMIT 1"));
        String status = expiryDate.isBefore(LocalDate.now()) ? "EXPIRED"
                : !expiryDate.isAfter(LocalDate.now().plusDays(NEAR_EXPIRY_DAYS)) ? "NEAR_EXPIRY"
                : "NORMAL";
        if (existing == null) {
            Batch batch = new Batch();
            batch.setSkuId(sampling.getSkuId());
            batch.setBatchNo(sampling.getBatchNo());
            batch.setProductionDate(productionDate);
            batch.setExpiryDate(expiryDate);
            batch.setStorageCondition(sampling.getStorageCondition());
            batch.setStatus(status);
            batch.setLastCheckAt(LocalDateTime.now());
            batchMapper.insert(batch);
        } else {
            existing.setProductionDate(productionDate);
            existing.setExpiryDate(expiryDate);
            existing.setStatus(status);
            existing.setLastCheckAt(LocalDateTime.now());
            batchMapper.updateById(existing);
        }
    }

    // ------------------------------------------------------------------
    // 留样登记 / 处置
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "样品留样", action = "RETAIN", bizType = BIZ_TYPE, bizIdExpr = "#request.id")
    public void retain(SampleRetainRequest request) {
        Sample sample = getRequired(request.getId());
        AuditContext.putBefore(writeJson(sample));
        if (request.getRetainUntil().isBefore(LocalDate.now())) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "留样到期日不能早于当天");
        }
        String next = stateMachine.nextState(BIZ_TYPE, sample.getStatus(), "RETAIN", currentRoles());
        sample.setStatus(next);
        sample.setRetainFlag(1);
        sample.setRetainLocation(request.getRetainLocation().trim());
        sample.setRetainUntil(request.getRetainUntil());
        updateWithLock(sample);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "样品处置", action = "DISPOSE", bizType = BIZ_TYPE, bizIdExpr = "#request.id")
    public void dispose(SampleDisposeRequest request) {
        Sample sample = getRequired(request.getId());
        AuditContext.putBefore(writeJson(sample));
        if (!"DESTROY".equals(request.getDisposeType()) && !"RETURN".equals(request.getDisposeType())) {
            throw new BizException(ResultCode.PARAM_INVALID, "处置方式仅支持 DESTROY/RETURN");
        }
        if ("RETAINING".equals(sample.getStatus()) && sample.getRetainUntil() != null
                && sample.getRetainUntil().isAfter(LocalDate.now())
                && "DESTROY".equals(request.getDisposeType())) {
            log.info("留样未到期销毁 sample={} 需说明留痕", sample.getSampleNo());
        }
        String next = stateMachine.nextState(BIZ_TYPE, sample.getStatus(), "DISPOSE", currentRoles());
        sample.setStatus(next);
        sample.setDisposeType(request.getDisposeType());
        sample.setDisposeRemark(request.getDisposeRemark().trim());
        sample.setDisposedAt(LocalDateTime.now());
        updateWithLock(sample);
    }

    // ------------------------------------------------------------------
    // 查询 / 扫码
    // ------------------------------------------------------------------

    public PageResult<SampleVO> page(PageRequest request, String sampleNo, String batchNo,
                                     String status, String sampleType, Long skuId) {
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
                .like(sampleNo != null && !sampleNo.isBlank(), Sample::getSampleNo, sampleNo)
                .like(batchNo != null && !batchNo.isBlank(), Sample::getBatchNo, batchNo)
                .eq(status != null && !status.isBlank(), Sample::getStatus, status)
                .eq(sampleType != null && !sampleType.isBlank(), Sample::getSampleType, sampleType)
                .eq(skuId != null, Sample::getSkuId, skuId)
                .orderByDesc(Sample::getId);
        Page<Sample> page = sampleMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        List<SampleVO> records = enrichWithTask(views.toSampleVOList(page.getRecords()));
        return PageResult.of(page, records);
    }

    public SampleVO detail(Long id) {
        Sample sample = getRequired(id);
        SampleVO vo = views.toSampleVO(sample);
        List<SampleVO> list = enrichWithTask(List.of(vo));
        return list.get(0);
    }

    /** 扫码：按样品条码精确查询 */
    public SampleVO barcode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "条码内容为空");
        }
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleNo, code.trim()).last("LIMIT 1"));
        if (sample == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "未查询到条码对应样品: " + code);
        }
        return detail(sample.getId());
    }

    private List<SampleVO> enrichWithTask(List<SampleVO> records) {
        if (records.isEmpty()) {
            return records;
        }
        List<Long> sampleIds = records.stream().map(SampleVO::getId).toList();
        List<InspectionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                .in(InspectionTask::getSampleId, sampleIds)
                .orderByDesc(InspectionTask::getRoundNo));
        Map<Long, InspectionTask> latestBySample = tasks.stream()
                .collect(Collectors.toMap(InspectionTask::getSampleId, t -> t, (a, b) -> a));
        records.forEach(vo -> {
            InspectionTask task = latestBySample.get(vo.getId());
            if (task != null) {
                vo.setTaskId(task.getId());
                vo.setTaskNo(task.getTaskNo());
                vo.setTaskStatus(task.getStatus());
            }
        });
        return records;
    }

    public Sample getRequired(Long id) {
        Sample sample = sampleMapper.selectById(id);
        if (sample == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "样品不存在");
        }
        return sample;
    }

    private void updateWithLock(Sample sample) {
        int rows = sampleMapper.updateById(sample);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    private Set<String> currentRoles() {
        return SecurityUtils.getLoginUser().getRoles();
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
