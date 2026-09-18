package com.qms.modules.inspection.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.inspection.service.InspectionReportService;
import com.qms.modules.inspection.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "质检报告")
@RestController
@RequestMapping("/api/v1/inspection")
@RequiredArgsConstructor
public class InspectionReportController {

    private final InspectionReportService reportService;

    @Operation(summary = "报告分页")
    @GetMapping("/reports")
    @PreAuthorize("@perm.has('inspection:report:view')")
    public R<PageResult<ReportVO>> page(PageRequest request,
                                        @RequestParam(required = false) String reportNo,
                                        @RequestParam(required = false) String conclusion,
                                        @RequestParam(required = false) String status) {
        return R.ok(reportService.page(request, reportNo, conclusion, status));
    }

    @Operation(summary = "报告详情")
    @GetMapping("/reports/{id}")
    @PreAuthorize("@perm.has('inspection:report:view')")
    public R<ReportVO> detail(@PathVariable Long id) {
        return R.ok(reportService.detail(id));
    }

    @Operation(summary = "按任务查报告")
    @GetMapping("/tasks/{id}/report")
    @PreAuthorize("@perm.has('inspection:report:view')")
    public R<ReportVO> byTask(@PathVariable Long id) {
        return R.ok(reportService.byTask(id));
    }

    @Operation(summary = "导出PDF（双签后可导出）")
    @GetMapping("/reports/{id}/pdf")
    @PreAuthorize("@perm.has('inspection:report:export')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        ReportVO vo = reportService.detail(id);
        byte[] pdf = reportService.pdfBytes(id);
        String fileName = URLEncoder.encode(vo.getReport().getReportNo() + ".pdf", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "签发报告（固化PDF附件，签发后只读）")
    @PostMapping("/reports/{id}/issue")
    @PreAuthorize("@perm.has('inspection:report:export')")
    public R<Void> issue(@PathVariable Long id) {
        reportService.issue(id);
        return R.ok();
    }
}
