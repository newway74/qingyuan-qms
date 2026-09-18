package com.qms.modules.ledger.controller;

import com.qms.common.result.R;
import com.qms.modules.ledger.service.DemoDataService;
import com.qms.modules.ledger.vo.DemoDataStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演示数据管理（一键清除）")
@RestController
@RequestMapping("/api/v1/ledger/demo")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoDataService demoDataService;

    @Operation(summary = "统计当前演示数据规模（清除前影响范围确认）")
    @GetMapping("/stats")
    @PreAuthorize("@perm.has('ledger:demo:view')")
    public R<DemoDataStatsVO> stats() {
        return R.ok(demoDataService.stats());
    }

    @Operation(summary = "一键清除全部演示数据（仅删除 DEMO；账号/主数据/模板/USER 数据保留）")
    @DeleteMapping
    @PreAuthorize("@perm.has('ledger:demo:clear')")
    public R<DemoDataStatsVO> clear() {
        return R.ok(demoDataService.clear());
    }
}
