package com.qms.modules.alert.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.alert.entity.Alert;
import com.qms.modules.alert.service.AlertScanService;
import com.qms.modules.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "质量预警")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertScanService alertScanService;

    @Operation(summary = "预警分页")
    @GetMapping
    @PreAuthorize("@perm.has('alert:list')")
    public R<PageResult<Alert>> page(PageRequest request,
                                     @RequestParam(required = false) String alertType,
                                     @RequestParam(required = false) Integer status) {
        return R.ok(alertService.page(request, alertType, status));
    }

    @Operation(summary = "人工处理预警")
    @PostMapping("/{id}/handle")
    @PreAuthorize("@perm.has('alert:handle')")
    public R<Void> handle(@PathVariable Long id) {
        alertService.handle(id);
        return R.ok();
    }

    @Operation(summary = "手动扫描预警（types 不传=四类全扫）")
    @PostMapping("/scan")
    @PreAuthorize("@perm.has('alert:scan')")
    public R<Map<String, AlertScanService.ScanStat>> scan(
            @RequestBody(required = false) ScanRequest request) {
        Set<String> types = request == null || request.types() == null || request.types().isEmpty()
                ? Set.of(AlertScanService.SLA, AlertScanService.RETAIN,
                        AlertScanService.LICENSE, AlertScanService.BATCH)
                : Arrays.stream(request.types().split(","))
                        .map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        return R.ok(alertScanService.run(types));
    }

    public record ScanRequest(String types) {
    }
}
