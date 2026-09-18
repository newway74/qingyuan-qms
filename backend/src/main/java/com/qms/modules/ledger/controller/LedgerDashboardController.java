package com.qms.modules.ledger.controller;

import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.ledger.service.LedgerDashboardService;
import com.qms.modules.ledger.vo.LedgerDashboardVO;
import com.qms.modules.ledger.vo.MaterialGapVO;
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

/**
 * 商品品控工作台（统计主页）：卡片/图表/缺口明细 + 三类质量报表导出。
 */
@Tag(name = "商品品控工作台")
@RestController
@RequestMapping("/api/v1/ledger/dashboard")
@RequiredArgsConstructor
public class LedgerDashboardController {

    private final LedgerDashboardService dashboardService;

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Operation(summary = "工作台统计：四张卡片 + 过会状态/当前节点/品类分布")
    @GetMapping("/summary")
    @PreAuthorize("@perm.has('ledger:dashboard:view')")
    public R<LedgerDashboardVO> summary() {
        return R.ok(dashboardService.summary());
    }

    @Operation(summary = "资料重点缺口明细分页（点击可跳转商品详情）")
    @GetMapping("/gaps")
    @PreAuthorize("@perm.has('ledger:dashboard:view')")
    public R<PageResult<MaterialGapVO>> gaps(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String keyword) {
        return R.ok(PageResult.of(dashboardService.gapPage(pageNum, pageSize, keyword)));
    }

    @Operation(summary = "导出商品台账（含流程总览，附带筛选条件说明）")
    @GetMapping("/export/goods")
    @PreAuthorize("@perm.has('ledger:goods:export')")
    public ResponseEntity<byte[]> exportGoods(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long categoryL1Id,
                                              @RequestParam(required = false) Long categoryL2Id,
                                              @RequestParam(required = false) String cooperateResult,
                                              @RequestParam(required = false) String currentNodeCode) {
        byte[] bytes = dashboardService.exportGoods(keyword, categoryL1Id, categoryL2Id,
                cooperateResult, currentNodeCode);
        return excel("商品品控台账", bytes);
    }

    @Operation(summary = "导出全流程记录（商品 × 节点）")
    @GetMapping("/export/flows")
    @PreAuthorize("@perm.has('ledger:goods:export')")
    public ResponseEntity<byte[]> exportFlows() {
        return excel("商品全流程记录", dashboardService.exportFlowRecords());
    }

    @Operation(summary = "导出资料重点缺口清单")
    @GetMapping("/export/gaps")
    @PreAuthorize("@perm.has('ledger:goods:export')")
    public ResponseEntity<byte[]> exportGaps() {
        return excel("资料重点缺口清单", dashboardService.exportMaterialGaps());
    }

    private ResponseEntity<byte[]> excel(String baseName, byte[] bytes) {
        String fileName = URLEncoder.encode(baseName + "_" + LedgerDashboardService.fileTimestamp() + ".xlsx",
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(XLSX)
                .body(bytes);
    }
}
