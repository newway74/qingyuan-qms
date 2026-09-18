package com.qms.modules.dashboard.vo;

import java.util.List;

/** 质量看板六图聚合 VO（口径由 DashboardMapper 统一供给） */
public class DashboardVo {

    public record NameValue(String name, long value) {}

    public record TrendPoint(String period, long total, long qualified,
                             long unqualified, double passRate) {}

    public record ParetoItem(String name, long count, double cumRate) {}

    public record RankRow(String name, long total, long qualified,
                          long unqualified, double passRate) {}

    public record EfficiencyPoint(String period, long finished, double avgHours) {}

    public record ChartsData(
            List<TrendPoint> passRateTrend,
            List<ParetoItem> defectPareto,
            List<NameValue> sourcePie,
            List<EfficiencyPoint> efficiency,
            List<NameValue> retainBuckets,
            List<NameValue> batchBuckets
    ) {}
}
