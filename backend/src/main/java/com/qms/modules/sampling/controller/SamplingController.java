package com.qms.modules.sampling.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.sampling.dto.SamplingCancelRequest;
import com.qms.modules.sampling.dto.SamplingUpsertRequest;
import com.qms.modules.sampling.service.SamplingService;
import com.qms.modules.sampling.vo.SamplingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "抽样管理")
@RestController
@RequestMapping("/api/v1/sampling")
@RequiredArgsConstructor
public class SamplingController {

    private final SamplingService samplingService;

    @Operation(summary = "抽样单分页")
    @GetMapping
    @PreAuthorize("@perm.has('sampling:list')")
    public R<PageResult<SamplingVO>> page(PageRequest request,
                                          @RequestParam(required = false) String samplingNo,
                                          @RequestParam(required = false) String source,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long skuId,
                                          @RequestParam(required = false) String batchNo,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(samplingService.page(request, samplingNo, source, status, skuId, batchNo, keyword));
    }

    @Operation(summary = "抽样单详情（含样品清单）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sampling:view')")
    public R<SamplingVO> detail(@PathVariable Long id) {
        return R.ok(samplingService.detail(id));
    }

    @Operation(summary = "新建抽样单草稿（生成CY编号）")
    @PostMapping
    @PreAuthorize("@perm.has('sampling:create')")
    public R<Long> create(@Valid @RequestBody SamplingUpsertRequest request) {
        return R.ok(samplingService.create(request));
    }

    @Operation(summary = "编辑草稿")
    @PostMapping("/update")
    @PreAuthorize("@perm.has('sampling:create')")
    public R<Void> update(@Valid @RequestBody SamplingUpsertRequest request) {
        samplingService.updateDraft(request);
        return R.ok();
    }

    @Operation(summary = "提交抽样单（校验+流程版本快照，进入待收样）")
    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.has('sampling:submit')")
    public R<Void> submit(@PathVariable Long id) {
        samplingService.submit(id);
        return R.ok();
    }

    @Operation(summary = "撤销抽样单（强制原因留痕）")
    @PostMapping("/cancel")
    @PreAuthorize("@perm.has('sampling:cancel')")
    public R<Void> cancel(@Valid @RequestBody SamplingCancelRequest request) {
        samplingService.cancel(request);
        return R.ok();
    }
}
