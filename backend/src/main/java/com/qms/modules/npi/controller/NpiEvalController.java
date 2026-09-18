package com.qms.modules.npi.controller;

import com.qms.common.result.R;
import com.qms.modules.npi.dto.EvalUpsertRequest;
import com.qms.modules.npi.service.NpiEvalService;
import com.qms.modules.npi.vo.EvalDetailVO;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "新品送样评估")
@RestController
@RequestMapping("/api/v1/npi/evals")
@RequiredArgsConstructor
public class NpiEvalController {

    private final NpiEvalService evalService;

    @Operation(summary = "评估单详情（含五维度评分）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('npi:project:view')")
    public R<EvalDetailVO> detail(@PathVariable Long id) {
        return R.ok(evalService.detail(id));
    }

    @Operation(summary = "新建/保存评估单（草稿，整组评分替换）")
    @PostMapping
    @PreAuthorize("@perm.has('npi:project:edit')")
    public R<Long> save(@Valid @RequestBody EvalUpsertRequest request) {
        return R.ok(evalService.save(request));
    }

    @Operation(summary = "提交评估（加权总分+同项目排名，时间线留痕）")
    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.has('npi:project:stage')")
    public R<Void> submit(@PathVariable Long id) {
        evalService.submit(id);
        return R.ok();
    }
}
