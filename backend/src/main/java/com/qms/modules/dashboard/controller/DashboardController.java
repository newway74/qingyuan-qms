package com.qms.modules.dashboard.controller;

import com.qms.common.result.R;
import com.qms.modules.dashboard.service.DashboardService;
import com.qms.modules.dashboard.service.MonthlyReportService;
import com.qms.modules.dashboard.vo.DashboardVo.ChartsData;
import com.qms.modules.dashboard.vo.DashboardVo.RankRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "质量看板：统计口径")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final MonthlyReportService monthlyReportService;

    @Operation(summary = "六图聚合（合格率趋势/帕累托/来源/时效/留样/批次）")
    @GetMapping("/charts")
    @PreAuthorize("@perm.has('dashboard:view')")
    public R<ChartsData> charts(@RequestParam(required = false) String from,
                                @RequestParam(required = false) String to,
                                @RequestParam(required = false) Long categoryId,
                                @RequestParam(required = false) Long supplierId) {
        return R.ok(dashboardService.charts(from, to, categoryId, supplierId));
    }

    @Operation(summary = "合格率排行：dim=CATEGORY/BRAND/SUPPLIER")
    @GetMapping("/ranking")
    @PreAuthorize("@perm.has('dashboard:view')")
    public R<List<RankRow>> ranking(@RequestParam(defaultValue = "CATEGORY") String dim,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) Long supplierId) {
        return R.ok(dashboardService.ranking(dim, from, to, categoryId, supplierId));
    }

    @Operation(summary = "导出质量月报 Excel（合格率/缺陷分布/供应商评级/预警处置）")
    @GetMapping("/monthly-report")
    @PreAuthorize("@perm.has('report:export')")
    public ResponseEntity<byte[]> monthlyReport(@RequestParam(required = false) String month) {
        byte[] bytes = monthlyReportService.export(month);
        String suffix = (month == null || month.isBlank())
                ? java.time.YearMonth.now().toString()
                : month;
        String fileName = URLEncoder.encode("质量月报-" + suffix + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
