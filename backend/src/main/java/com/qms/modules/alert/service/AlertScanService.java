package com.qms.modules.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.modules.alert.entity.Alert;
import com.qms.modules.alert.mapper.AlertMapper;
import com.qms.modules.inspection.entity.InspectionTask;
import com.qms.modules.inspection.mapper.InspectionTaskMapper;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.entity.SupplierLicense;
import com.qms.modules.masterdata.mapper.SupplierLicenseMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.sampling.entity.Batch;
import com.qms.modules.sampling.entity.Sample;
import com.qms.modules.sampling.mapper.BatchMapper;
import com.qms.modules.sampling.mapper.SampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 四类预警扫描：SLA 超期、留样到期、证照到期、批次近效期/过期。
 * 去重靠 qc_alert.dedup_key；每轮扫描以"仍命中集合"对账，条件解除自动核销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertScanService {

    public static final String SLA = "SLA";
    public static final String RETAIN = "RETAIN";
    public static final String LICENSE = "LICENSE";
    public static final String BATCH = "BATCH";
    private static final Set<String> ALL_TYPES = Set.of(SLA, RETAIN, LICENSE, BATCH);

    private static final String ROLE_QA = "QA_MANAGER";
    private static final List<String> ACTIVE_TASK_STATUS = List.of("PENDING_INSPECT", "INSPECTING");

    private final AlertService alertService;
    private final AlertMapper alertMapper;
    private final InspectionTaskMapper taskMapper;
    private final SampleMapper sampleMapper;
    private final SupplierLicenseMapper licenseMapper;
    private final SupplierMapper supplierMapper;
    private final BatchMapper batchMapper;

    @Value("${qms.alert.sla-warning-hours:24}")
    private long slaWarningHours;
    @Value("${qms.alert.retain-warning-days:30}")
    private long retainWarningDays;
    @Value("${qms.alert.license-warning-days:60}")
    private long licenseWarningDays;
    @Value("${qms.alert.batch-warning-days:30}")
    private long batchWarningDays;

    // ---------------- 定时调度（Spring Scheduling，预留 XXL-Job 切换） ----------------
    // 注意：调度入口必须兜住所有异常。单次扫描失败只记录日志，绝不允许异常逃逸后
    // 影响调度线程对下一轮任务的执行。

    @Scheduled(cron = "${qms.alert.sla-cron:0 0 * * * ?}")
    public void scheduledSla() {
        long start = System.currentTimeMillis();
        try {
            ScanStat stat = runOne(SLA);
            log.info("[alert-scan] 定时 SLA 扫描完成，耗时 {} ms，新增 {} 条，核销 {} 条",
                    System.currentTimeMillis() - start, stat.raised(), stat.resolved());
        } catch (Exception e) {
            // 整点扫描绝不能因单轮异常（DB/Redis 抖动等）影响下一轮整点执行
            log.error("[alert-scan] 定时 SLA 扫描失败，本轮跳过，下轮整点自动重试: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${qms.alert.daily-cron:0 30 8 * * ?}")
    public void scheduledDaily() {
        long start = System.currentTimeMillis();
        try {
            Map<String, ScanStat> stats = run(Set.of(RETAIN, LICENSE, BATCH));
            log.info("[alert-scan] 定时每日预警扫描完成，耗时 {} ms，明细 {}",
                    System.currentTimeMillis() - start, stats);
        } catch (Exception e) {
            log.error("[alert-scan] 定时每日预警扫描失败，本轮跳过，明日自动重试: {}", e.getMessage(), e);
        }
    }

    // ---------------- 手动/定时入口 ----------------

    public Map<String, ScanStat> run(Set<String> types) {
        Map<String, ScanStat> result = new LinkedHashMap<>();
        for (String type : types) {
            if (!ALL_TYPES.contains(type)) {
                continue;
            }
            // 逐类型隔离：某一类扫描失败不影响其余类型（如证照异常不耽误批次效期预警）
            try {
                result.put(type, runOne(type));
            } catch (Exception e) {
                log.error("[alert-scan] 类型 {} 扫描失败，跳过该类型", type, e);
                result.put(type, new ScanStat(0, 0));
            }
        }
        return result;
    }

    /**
     * 单类扫描入口。不使用类级别大事务：{@link AlertService#raise} 自身具备独立事务，
     * 每条预警短事务提交，锁持有时间最短；且避免自调用导致 @Transactional 代理失效。
     */
    public ScanStat runOne(String type) {
        long start = System.currentTimeMillis();
        ScanStat stat = switch (type) {
            case SLA -> scanSla();
            case RETAIN -> scanRetain();
            case LICENSE -> scanLicense();
            case BATCH -> scanBatch();
            default -> new ScanStat(0, 0);
        };
        log.info("[alert-scan] type={} raised={} resolved={} 耗时={}ms",
                type, stat.raised(), stat.resolved(), System.currentTimeMillis() - start);
        return stat;
    }

    // ---------------- SLA ----------------

    private ScanStat scanSla() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warnLine = now.plusHours(slaWarningHours);
        Set<String> hitKeys = new HashSet<>();
        long raised = 0;
        List<InspectionTask> active = taskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                .in(InspectionTask::getStatus, ACTIVE_TASK_STATUS)
                .isNotNull(InspectionTask::getSlaDeadline));
        for (InspectionTask task : active) {
            if (task.getSlaDeadline().isBefore(now)) {
                String key = "SLA_OVERDUE:" + task.getId();
                hitKeys.add(key);
                raised += alertService.raise("SLA_OVERDUE", "qc_inspection_task", task.getId(), ROLE_QA, 3,
                        "检验任务 " + task.getTaskNo() + " 已超 SLA 截止 " + fmt(task.getSlaDeadline()), key) ? 1 : 0;
            } else if (!task.getSlaDeadline().isAfter(warnLine)) {
                String key = "SLA_WARNING:" + task.getId();
                hitKeys.add(key);
                raised += alertService.raise("SLA_WARNING", "qc_inspection_task", task.getId(), ROLE_QA, 2,
                        "检验任务 " + task.getTaskNo() + " 将于 " + fmt(task.getSlaDeadline()) + " 超 SLA", key) ? 1 : 0;
            }
        }
        long resolved = reconcile(Set.of("SLA_WARNING", "SLA_OVERDUE"), hitKeys);
        return new ScanStat(raised, resolved);
    }

    // ---------------- 留样到期 ----------------

    private ScanStat scanRetain() {
        LocalDate today = LocalDate.now();
        LocalDate warnLine = today.plusDays(retainWarningDays);
        Set<String> hitKeys = new HashSet<>();
        long raised = 0;
        List<Sample> retains = sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleType, "RETAIN")
                .eq(Sample::getStatus, "RETAINING")
                .isNotNull(Sample::getRetainUntil));
        for (Sample sample : retains) {
            LocalDate until = sample.getRetainUntil();
            if (until.isAfter(warnLine)) {
                continue;
            }
            String key = "RETAIN_EXPIRE:" + sample.getId();
            hitKeys.add(key);
            int level = until.isBefore(today) ? 3 : 2;
            String tail = until.isBefore(today) ? "已于 " + until + " 到期" : "将于 " + until + " 到期";
            raised += alertService.raise("RETAIN_EXPIRE", "qc_sample", sample.getId(), ROLE_QA, level,
                    "留样 " + sample.getSampleNo() + tail + "，请安排处置", key) ? 1 : 0;
        }
        long resolved = reconcile(Set.of("RETAIN_EXPIRE"), hitKeys);
        return new ScanStat(raised, resolved);
    }

    // ---------------- 证照到期 ----------------

    private ScanStat scanLicense() {
        LocalDate today = LocalDate.now();
        LocalDate warnLine = today.plusDays(licenseWarningDays);
        Set<String> hitKeys = new HashSet<>();
        long raised = 0;
        List<SupplierLicense> licenses = licenseMapper.selectList(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getStatus, 1)
                .isNotNull(SupplierLicense::getValidTo)
                .le(SupplierLicense::getValidTo, warnLine));
        Map<Long, String> supplierNames = supplierMapper.selectBatchIds(
                        licenses.stream().map(SupplierLicense::getSupplierId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));
        for (SupplierLicense license : licenses) {
            String key = "LICENSE_EXPIRE:" + license.getId();
            hitKeys.add(key);
            int level = license.getValidTo().isBefore(today) ? 3 : 2;
            String tail = license.getValidTo().isBefore(today)
                    ? "已于 " + license.getValidTo() + " 过期" : "将于 " + license.getValidTo() + " 到期";
            String name = supplierNames.getOrDefault(license.getSupplierId(), "供应商#" + license.getSupplierId());
            raised += alertService.raise("LICENSE_EXPIRE", "qc_supplier_license", license.getId(), ROLE_QA, level,
                    name + "「" + license.getLicenseType() + "」" + tail, key) ? 1 : 0;
        }
        long resolved = reconcile(Set.of("LICENSE_EXPIRE"), hitKeys);
        return new ScanStat(raised, resolved);
    }

    // ---------------- 批次近效期/过期 ----------------

    private ScanStat scanBatch() {
        LocalDate today = LocalDate.now();
        LocalDate warnLine = today.plusDays(batchWarningDays);
        Set<String> hitKeys = new HashSet<>();
        long raised = 0;
        List<Batch> batches = batchMapper.selectList(new LambdaQueryWrapper<Batch>()
                .isNotNull(Batch::getExpiryDate)
                .le(Batch::getExpiryDate, warnLine));
        for (Batch batch : batches) {
            String tail = "批号 " + batch.getBatchNo();
            if (batch.getExpiryDate().isBefore(today)) {
                String key = "BATCH_EXPIRED:" + batch.getId();
                hitKeys.add(key);
                raised += alertService.raise("BATCH_EXPIRED", "qc_batch", batch.getId(), ROLE_QA, 3,
                        tail + " 已于 " + batch.getExpiryDate() + " 过期", key) ? 1 : 0;
            } else {
                String key = "NEAR_EXPIRY:" + batch.getId();
                hitKeys.add(key);
                raised += alertService.raise("NEAR_EXPIRY", "qc_batch", batch.getId(), ROLE_QA, 2,
                        tail + " 将于 " + batch.getExpiryDate() + " 到效期", key) ? 1 : 0;
            }
        }
        long resolved = reconcile(Set.of("NEAR_EXPIRY", "BATCH_EXPIRED"), hitKeys);
        return new ScanStat(raised, resolved);
    }

    /**
     * 解除对账：指定类型的未处理预警中 dedup_key 不在本轮命中集合的，说明触发条件已消失，自动核销。
     */
    private long reconcile(Set<String> alertTypes, Set<String> hitKeys) {
        List<Alert> open = alertMapper.selectList(new LambdaQueryWrapper<Alert>()
                .in(Alert::getAlertType, alertTypes)
                .eq(Alert::getStatus, 0));
        long resolved = 0;
        for (Alert alert : open) {
            if (!hitKeys.contains(alert.getDedupKey())) {
                alertService.resolveByDedupPrefix(alert.getAlertType(), alert.getBizType(), alert.getBizId());
                resolved++;
            }
        }
        return resolved;
    }

    private static String fmt(LocalDateTime time) {
        return time.toString().replace('T', ' ');
    }

    /** raised=本轮新生成；resolved=条件解除自动核销 */
    public record ScanStat(long raised, long resolved) {
    }
}
