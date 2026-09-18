package com.qms.modules.sampling.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.sampling.entity.Batch;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.mapper.BatchMapper;
import com.qms.modules.sampling.mapper.SampleMapper;
import com.qms.modules.sampling.vo.BatchVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次效期台账：分页查询（SKU/产品冗余）、按效期重算状态。
 * 近效期阈值：30 天（阶段5 配置化/定时任务化）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    public static final long NEAR_EXPIRY_DAYS = 30;

    private final BatchMapper batchMapper;
    private final SampleMapper sampleMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;

    public PageResult<BatchVO> page(PageRequest request, String batchNo, String status, Long skuId) {
        LambdaQueryWrapper<Batch> wrapper = new LambdaQueryWrapper<Batch>()
                .eq(Batch::getTenantId, SecurityUtils.getTenantId())
                .like(batchNo != null && !batchNo.isBlank(), Batch::getBatchNo, batchNo)
                .eq(status != null && !status.isBlank(), Batch::getStatus, status)
                .eq(skuId != null, Batch::getSkuId, skuId)
                .orderByAsc(Batch::getExpiryDate)
                .orderByDesc(Batch::getId);
        Page<Batch> page = batchMapper.selectPage(
                new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toVO).toList());
    }

    /** 效期看板顶部统计：正常/近效期/已过期数量。 */
    public Map<String, Long> summary() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("NORMAL", countByStatus("NORMAL"));
        result.put("NEAR_EXPIRY", countByStatus("NEAR_EXPIRY"));
        result.put("EXPIRED", countByStatus("EXPIRED"));
        return result;
    }

    private Long countByStatus(String status) {
        Long c = batchMapper.selectCount(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getTenantId, SecurityUtils.getTenantId())
                .eq(Batch::getStatus, status));
        return c == null ? 0L : c;
    }

    /**
     * 手动重算全量批次状态：
     * expiry_date < 今天 → EXPIRED；≤ 今天+30天 → NEAR_EXPIRY；否则 NORMAL。
     * 已过期状态不回退为近效期/正常。
     */
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "批次效期", action = "RECOMPUTE", bizType = "qc_batch")
    public Map<String, Integer> recompute() {
        List<Batch> batches = batchMapper.selectList(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getTenantId, SecurityUtils.getTenantId()));
        LocalDate today = LocalDate.now();
        int changed = 0;
        for (Batch batch : batches) {
            if (batch.getExpiryDate() == null) {
                continue;
            }
            String expected = expectedStatus(batch.getExpiryDate(), today);
            if (!expected.equals(batch.getStatus())) {
                batch.setStatus(expected);
                batch.setLastCheckAt(LocalDateTime.now());
                batchMapper.updateById(batch);
                changed++;
            }
        }
        log.info("批次效期重算完成 scanned={} changed={}", batches.size(), changed);
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("scanned", batches.size());
        result.put("changed", changed);
        return result;
    }

    /** 收样 upsert 批次与重算共用同一口径。 */
    public static String expectedStatus(LocalDate expiryDate, LocalDate today) {
        if (expiryDate.isBefore(today)) {
            return "EXPIRED";
        }
        if (!expiryDate.isAfter(today.plusDays(NEAR_EXPIRY_DAYS))) {
            return "NEAR_EXPIRY";
        }
        return "NORMAL";
    }

    private BatchVO toVO(Batch batch) {
        BatchVO vo = new BatchVO();
        vo.setBatch(batch);
        ProductSku sku = productSkuMapper.selectById(batch.getSkuId());
        if (sku != null) {
            vo.setSkuCode(sku.getSkuCode());
            vo.setSpec(sku.getSpec());
            Product product = productMapper.selectById(sku.getProductId());
            if (product != null) {
                vo.setProductName(product.getProductName());
            }
        }
        if (batch.getExpiryDate() != null) {
            vo.setDaysToExpiry(ChronoUnit.DAYS.between(LocalDate.now(), batch.getExpiryDate()));
        }
        Sample latest = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSkuId, batch.getSkuId())
                .eq(Sample::getBatchNo, batch.getBatchNo())
                .orderByDesc(Sample::getReceivedAt)
                .last("LIMIT 1"));
        if (latest != null && latest.getReceivedAt() != null) {
            vo.setLatestReceiveDate(latest.getReceivedAt().toLocalDate());
        }
        vo.setLatestCheckAt(batch.getLastCheckAt());
        return vo;
    }
}
