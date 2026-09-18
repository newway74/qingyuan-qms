package com.qms.modules.dashboard.service;

import com.qms.modules.dashboard.mapper.DashboardMapper;
import com.qms.modules.dashboard.vo.DashboardVo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 质量看板服务：口径只做 Mapper 聚合结果的转换，不在内存另算业务总数。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper mapper;

    public ChartsData charts(String from, String to, Long categoryId, Long supplierId) {
        LocalDateTime fromTs = parseStart(from);
        LocalDateTime toTs = parseEnd(to);

        List<TrendPoint> trend = new ArrayList<>();
        for (Map<String, Object> row : mapper.passRateTrend(fromTs, toTs, categoryId, supplierId)) {
            long total = num(row.get("total"));
            long qualified = num(row.get("qualified"));
            trend.add(new TrendPoint(
                    str(row.get("period")), total, qualified, total - qualified,
                    rate(qualified, total)));
        }

        List<Map<String, Object>> paretoRows = mapper.defectPareto(fromTs, toTs);
        long paretoTotal = mapper.defectParetoTotal(fromTs, toTs);
        List<ParetoItem> pareto = new ArrayList<>();
        long cumulative = 0;
        for (Map<String, Object> row : paretoRows) {
            long cnt = num(row.get("cnt"));
            cumulative += cnt;
            String name = str(row.get("name"));
            pareto.add(new ParetoItem(name == null || name.isBlank() ? "（未填项目）" : name,
                    cnt, rate(cumulative, paretoTotal)));
        }

        List<NameValue> sourcePie = new ArrayList<>();
        for (Map<String, Object> row : mapper.sourcePie(fromTs, toTs, categoryId, supplierId)) {
            sourcePie.add(new NameValue(str(row.get("name")), num(row.get("value"))));
        }

        List<EfficiencyPoint> efficiency = new ArrayList<>();
        for (Map<String, Object> row : mapper.efficiency(fromTs, toTs, categoryId, supplierId)) {
            efficiency.add(new EfficiencyPoint(str(row.get("period")),
                    num(row.get("finished")), dec(row.get("avgHours"))));
        }

        Map<String, Object> retain = mapper.retainBuckets();
        List<NameValue> retainBuckets = List.of(
                new NameValue("已过期", num(retain.get("expired"))),
                new NameValue("30天内到期", num(retain.get("d30"))),
                new NameValue("31-90天到期", num(retain.get("d90"))),
                new NameValue("90天以上", num(retain.get("d90plus"))));

        List<NameValue> batchBuckets = new ArrayList<>();
        for (Map<String, Object> row : mapper.batchBuckets()) {
            batchBuckets.add(new NameValue(str(row.get("name")), num(row.get("value"))));
        }

        return new ChartsData(trend, pareto, sourcePie, efficiency, retainBuckets, batchBuckets);
    }

    /** 合格率排行：dim=CATEGORY/BRAND/SUPPLIER */
    public List<RankRow> ranking(String dim, String from, String to,
                                 Long categoryId, Long supplierId) {
        LocalDateTime fromTs = parseStart(from);
        LocalDateTime toTs = parseEnd(to);
        List<Map<String, Object>> rows = switch (dim == null ? "CATEGORY" : dim) {
            case "BRAND" -> mapper.brandRanking(fromTs, toTs, categoryId, supplierId);
            case "SUPPLIER" -> mapper.supplierRanking(fromTs, toTs, categoryId, supplierId);
            default -> mapper.categoryRanking(fromTs, toTs, categoryId, supplierId);
        };
        List<RankRow> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long total = num(row.get("total"));
            long qualified = num(row.get("qualified"));
            result.add(new RankRow(str(row.get("name")), total, qualified,
                    total - qualified, rate(qualified, total)));
        }
        return result;
    }

    private static double rate(long part, long total) {
        return total == 0 ? 0d : Math.round(part * 10000d / total) / 100d;
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static double dec(Object o) {
        return o == null ? 0d : ((Number) o).doubleValue();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static LocalDateTime parseStart(String from) {
        return from == null || from.isBlank() ? null : LocalDate.parse(from).atStartOfDay();
    }

    private static LocalDateTime parseEnd(String to) {
        return to == null || to.isBlank() ? null
                : LocalDate.parse(to).atTime(23, 59, 59);
    }
}
