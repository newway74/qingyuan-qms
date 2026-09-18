package com.qms.modules.dashboard.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.alert.service.AlertService;
import com.qms.modules.dashboard.mapper.DashboardMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 质量月报 Excel 导出。统计口径与看板一致（双签 SIGNED 报告、复核签署时间归属月份）。
 */
@Service
@RequiredArgsConstructor
public class MonthlyReportService {

    private final DashboardMapper mapper;

    @AuditLog(module = "质量月报", action = "EXPORT", bizType = "qc_monthly_report")
    public byte[] export(String month) {
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        String period = ym.format(DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);

        // 总览：月度趋势最多一行
        List<Map<String, Object>> trend = mapper.passRateTrend(from, to, null, null);
        long total = 0;
        long qualified = 0;
        if (!trend.isEmpty()) {
            Map<String, Object> row = trend.get(0);
            total = num(row.get("total"));
            qualified = num(row.get("qualified"));
        }

        List<Map<String, Object>> defects = mapper.monthlyDefects(from, to);
        long defectA = 0;
        long defectB = 0;
        long defectC = 0;
        for (Map<String, Object> d : defects) {
            defectA += num(d.get("aCount"));
            defectB += num(d.get("bCount"));
            defectC += num(d.get("cCount"));
        }

        List<Map<String, Object>> alerts = mapper.monthlyAlerts(from, to);
        long alertRaised = 0;
        long alertHandled = 0;
        long alertOpen = 0;
        List<AlertRow> alertRows = new ArrayList<>();
        for (Map<String, Object> a : alerts) {
            long raised = num(a.get("raised"));
            long handled = num(a.get("handled"));
            long open = num(a.get("openAtEnd"));
            alertRaised += raised;
            alertHandled += handled;
            alertOpen += open;
            // 只写与本月有关或仍有未处理的类型，避免历史类型空行噪音
            if (raised > 0 || handled > 0 || open > 0) {
                alertRows.add(new AlertRow(AlertService.alertTypeName(String.valueOf(a.get("name"))),
                        raised, handled, open));
            }
        }

        List<OverviewRow> overview = new ArrayList<>();
        overview.add(new OverviewRow("报告月份", ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))));
        overview.add(new OverviewRow("判定报告总数", String.valueOf(total)));
        overview.add(new OverviewRow("合格报告数", String.valueOf(qualified)));
        overview.add(new OverviewRow("不合格报告数", String.valueOf(total - qualified)));
        overview.add(new OverviewRow("合格率", (total == 0 ? 0d : Math.round(qualified * 10000d / total) / 100d) + "%"));
        overview.add(new OverviewRow("A类缺陷次数", String.valueOf(defectA)));
        overview.add(new OverviewRow("B类缺陷次数", String.valueOf(defectB)));
        overview.add(new OverviewRow("C类缺陷次数", String.valueOf(defectC)));
        overview.add(new OverviewRow("本月新增预警", String.valueOf(alertRaised)));
        overview.add(new OverviewRow("本月处置预警", String.valueOf(alertHandled)));
        overview.add(new OverviewRow("期末未处理预警", String.valueOf(alertOpen)));

        List<SupplierRow> supplierRows = new ArrayList<>();
        for (Map<String, Object> s : mapper.monthlySuppliers(from, to, null, null, period)) {
            long t = num(s.get("total"));
            long q = num(s.get("qualified"));
            double rate = t == 0 ? 0d : Math.round(q * 10000d / t) / 100d;
            supplierRows.add(new SupplierRow(str(s.get("name")), t, q, t - q, rate + "%",
                    str(s.get("grade"))));
        }

        List<DefectRow> defectRows = new ArrayList<>();
        for (Map<String, Object> d : defects) {
            defectRows.add(new DefectRow(str(d.get("name")),
                    num(d.get("aCount")), num(d.get("bCount")), num(d.get("cCount")), num(d.get("total"))));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(out)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .build()) {
            WriteSheet s1 = EasyExcel.writerSheet(0, "质量总览").head(OverviewRow.class).build();
            WriteSheet s2 = EasyExcel.writerSheet(1, "供应商评级").head(SupplierRow.class).build();
            WriteSheet s3 = EasyExcel.writerSheet(2, "缺陷分布").head(DefectRow.class).build();
            WriteSheet s4 = EasyExcel.writerSheet(3, "预警处置").head(AlertRow.class).build();
            writer.write(overview, s1);
            writer.write(supplierRows, s2);
            writer.write(defectRows, s3);
            writer.write(alertRows, s4);
        }
        return out.toByteArray();
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @Data
    @ColumnWidth(22)
    public static class OverviewRow {
        @ExcelProperty("指标")
        private String item;
        @ExcelProperty("数值")
        private String value;

        public OverviewRow(String item, String value) {
            this.item = item;
            this.value = value;
        }
    }

    @Data
    @ColumnWidth(20)
    public static class SupplierRow {
        @ExcelProperty("供应商")
        private String name;
        @ExcelProperty("判定批次")
        private long total;
        @ExcelProperty("合格数")
        private long qualified;
        @ExcelProperty("不合格数")
        private long unqualified;
        @ExcelProperty("合格率")
        private String passRate;
        @ExcelProperty("当月评级")
        private String grade;

        public SupplierRow(String name, long total, long qualified, long unqualified,
                           String passRate, String grade) {
            this.name = name;
            this.total = total;
            this.qualified = qualified;
            this.unqualified = unqualified;
            this.passRate = passRate;
            this.grade = grade;
        }
    }

    @Data
    @ColumnWidth(36)
    public static class DefectRow {
        @ExcelProperty("缺陷项")
        private String name;
        @ExcelProperty("A类次数")
        private long aCount;
        @ExcelProperty("B类次数")
        private long bCount;
        @ExcelProperty("C类次数")
        private long cCount;
        @ExcelProperty("合计")
        private long total;

        public DefectRow(String name, long aCount, long bCount, long cCount, long total) {
            this.name = name;
            this.aCount = aCount;
            this.bCount = bCount;
            this.cCount = cCount;
            this.total = total;
        }
    }

    @Data
    @ColumnWidth(22)
    public static class AlertRow {
        @ExcelProperty("预警类型")
        private String name;
        @ExcelProperty("本月新增")
        private long raised;
        @ExcelProperty("本月处置")
        private long handled;
        @ExcelProperty("期末未处理")
        private long openAtEnd;

        public AlertRow(String name, long raised, long handled, long openAtEnd) {
            this.name = name;
            this.raised = raised;
            this.handled = handled;
            this.openAtEnd = openAtEnd;
        }
    }
}
