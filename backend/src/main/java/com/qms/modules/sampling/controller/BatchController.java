package com.qms.modules.sampling.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.sampling.service.BatchService;
import com.qms.modules.sampling.vo.BatchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "批次效期台账")
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @Operation(summary = "批次台账分页（按效期升序，近效期/过期优先）")
    @GetMapping
    @PreAuthorize("@perm.has('batch:list')")
    public R<PageResult<BatchVO>> page(PageRequest request,
                                       @RequestParam(required = false) String batchNo,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long skuId) {
        return R.ok(batchService.page(request, batchNo, status, skuId));
    }

    @Operation(summary = "效期状态计数")
    @GetMapping("/summary")
    @PreAuthorize("@perm.has('batch:list')")
    public R<Map<String, Long>> summary() {
        return R.ok(batchService.summary());
    }

    @Operation(summary = "手动重算批次效期状态（近效期阈值30天）")
    @PostMapping("/recompute")
    @PreAuthorize("@perm.has('batch:recompute')")
    public R<Map<String, Integer>> recompute() {
        return R.ok(batchService.recompute());
    }
}
