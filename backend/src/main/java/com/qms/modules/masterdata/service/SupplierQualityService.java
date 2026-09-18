package com.qms.modules.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.modules.masterdata.entity.SupplierQuality;
import com.qms.modules.masterdata.mapper.SupplierQualityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商质量评级沉淀：单据闭环时按周期（yyyyMM）重算并 upsert 评级快照。
 *
 * 口径：
 * 1) 同一样品（批次）在周期内可能有原检+复检多份报告，以最新一份结论为准；
 * 2) 合格率 = 最新结论为 QUALIFIED 的批次数 / 总批次数 ×100；让步接收(CONCESSION)计为不合格批；
 * 3) 得分 = 合格率 − 15×A类批 − 8×B类批 − 3×C类批（下限0）；
 * 4) 等级 A≥95 / B≥85 / C≥70 / D&lt;70；当期存在 A 类不合格批时等级最高 C。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierQualityService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;
    private final SupplierQualityMapper qualityMapper;

    /** 按样品（批次链）重算其供应商当期评级。 */
    @Transactional(rollbackFor = Exception.class)
    public void settleBySample(Long sampleId) {
        Long supplierId = jdbcTemplate.queryForObject(
                "SELECT p.supplier_id FROM qc_product p "
                        + "JOIN qc_product_sku k ON k.product_id = p.id AND k.deleted = 0 "
                        + "JOIN qc_sample s ON s.sku_id = k.id AND s.deleted = 0 "
                        + "WHERE s.id = ? AND p.deleted = 0",
                Long.class, sampleId);
        if (supplierId == null) {
            log.warn("样品 {} 无法解析供应商，跳过评级沉淀", sampleId);
            return;
        }
        settleSupplier(supplierId, LocalDateTime.now().format(PERIOD_FMT));
    }

    /** 重算指定供应商指定周期评级并 upsert。 */
    @Transactional(rollbackFor = Exception.class)
    public SupplierQuality settleSupplier(Long supplierId, String period) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT t.sample_id AS sample_id, r.conclusion AS conclusion, "
                        + "r.a_fail_count AS a_cnt, r.b_fail_count AS b_cnt, r.c_fail_count AS c_cnt, "
                        + "r.issued_at AS issued_at, r.created_at AS created_at, r.id AS report_id "
                        + "FROM qc_inspection_report r "
                        + "JOIN qc_inspection_task t ON t.id = r.task_id AND t.deleted = 0 "
                        + "JOIN qc_sample s ON s.id = t.sample_id AND s.deleted = 0 "
                        + "JOIN qc_product_sku k ON k.id = s.sku_id AND k.deleted = 0 "
                        + "JOIN qc_product p ON p.id = k.product_id AND p.deleted = 0 "
                        + "WHERE p.supplier_id = ? AND r.deleted = 0 AND r.status <> 'DRAFT' "
                        + "AND DATE_FORMAT(COALESCE(r.issued_at, r.created_at), '%Y%m') = ?",
                supplierId, period);

        // 同一样品多份报告（原检/复检）取最新一份
        Map<Long, Map<String, Object>> latestBySample = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long sampleId = ((Number) row.get("sample_id")).longValue();
            Map<String, Object> exist = latestBySample.get(sampleId);
            if (exist == null || reportTime(row).isAfter(reportTime(exist))) {
                latestBySample.put(sampleId, row);
            }
        }

        int batchCount = latestBySample.size();
        int qualified = 0;
        int aBatches = 0;
        int bBatches = 0;
        int cBatches = 0;
        for (Map<String, Object> row : latestBySample.values()) {
            String conclusion = (String) row.get("conclusion");
            if ("QUALIFIED".equals(conclusion)) {
                qualified++;
            } else {
                if (((Number) row.get("a_cnt")).intValue() > 0) {
                    aBatches++;
                }
                if (((Number) row.get("b_cnt")).intValue() > 0) {
                    bBatches++;
                }
                if (((Number) row.get("c_cnt")).intValue() > 0) {
                    cBatches++;
                }
            }
        }
        int defectCount = batchCount - qualified;

        SupplierQuality quality = qualityMapper.selectOne(new LambdaQueryWrapper<SupplierQuality>()
                .eq(SupplierQuality::getSupplierId, supplierId)
                .eq(SupplierQuality::getPeriod, period)
                .last("LIMIT 1"));
        boolean isNew = quality == null;
        if (isNew) {
            quality = new SupplierQuality();
            quality.setSupplierId(supplierId);
            quality.setPeriod(period);
        }
        quality.setBatchCount(batchCount);
        quality.setDefectCount(defectCount);
        if (batchCount == 0) {
            quality.setPassRate(null);
            quality.setScore(null);
            quality.setGrade(null);
        } else {
            BigDecimal passRate = BigDecimal.valueOf(qualified * 100.0 / batchCount)
                    .setScale(2, RoundingMode.HALF_UP);
            quality.setPassRate(passRate);
            double score = Math.max(0, passRate.doubleValue() - 15.0 * aBatches - 8.0 * bBatches - 3.0 * cBatches);
            quality.setScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
            String grade = score >= 95 ? "A" : score >= 85 ? "B" : score >= 70 ? "C" : "D";
            // 当期存在 A 类不合格批：等级最高 C（安全底线）
            if (aBatches > 0 && (grade.equals("A") || grade.equals("B"))) {
                grade = "C";
            }
            quality.setGrade(grade);
        }
        if (isNew) {
            qualityMapper.insert(quality);
        } else {
            qualityMapper.updateById(quality);
        }
        log.info("供应商评级沉淀 supplier={} period={} batches={} pass={} grade={}",
                supplierId, period, batchCount, quality.getPassRate(), quality.getGrade());
        return quality;
    }

    public List<SupplierQuality> trend(Long supplierId) {
        return qualityMapper.selectList(new LambdaQueryWrapper<SupplierQuality>()
                .eq(SupplierQuality::getSupplierId, supplierId)
                .orderByAsc(SupplierQuality::getPeriod));
    }

    private LocalDateTime reportTime(Map<String, Object> row) {
        Object t = row.get("issued_at");
        if (t == null) {
            t = row.get("created_at");
        }
        return (LocalDateTime) t;
    }
}
