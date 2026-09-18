package com.qms.modules.process.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.process.dto.NodeBatchSaveRequest;
import com.qms.modules.process.dto.ProcessDefUpsertRequest;
import com.qms.modules.process.entity.ProcessChangeLog;
import com.qms.modules.process.entity.ProcessDef;
import com.qms.modules.process.service.ProcessDefService;
import com.qms.modules.process.vo.ProcessDefDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "品控流程配置")
@RestController
@RequestMapping("/api/v1/process/defs")
@RequiredArgsConstructor
public class ProcessDefController {

    private final ProcessDefService processDefService;

    @Operation(summary = "流程定义分页（含全部版本行）")
    @GetMapping
    @PreAuthorize("@perm.has('process:def:list')")
    public R<PageResult<ProcessDef>> page(PageRequest request,
                                          @RequestParam(required = false) String processName,
                                          @RequestParam(required = false) String processCode,
                                          @RequestParam(required = false) String status) {
        return R.ok(processDefService.page(request, processName, processCode, status));
    }

    @Operation(summary = "当前生效流程定义（含九节点）")
    @GetMapping("/effective")
    @PreAuthorize("@perm.has('process:def:list')")
    public R<ProcessDefDetailVO> effective() {
        return R.ok(processDefService.effective());
    }

    @Operation(summary = "流程定义详情（含节点）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('process:def:list')")
    public R<ProcessDefDetailVO> detail(@PathVariable Long id) {
        return R.ok(processDefService.detail(id));
    }

    @Operation(summary = "版本列表")
    @GetMapping("/{id}/versions")
    @PreAuthorize("@perm.has('process:def:list')")
    public R<List<ProcessDef>> versions(@PathVariable Long id) {
        return R.ok(processDefService.versions(id));
    }

    @Operation(summary = "发布变更记录（diff）")
    @GetMapping("/{id}/changelog")
    @PreAuthorize("@perm.has('process:def:list')")
    public R<List<ProcessChangeLog>> changelog(@PathVariable Long id) {
        return R.ok(processDefService.changelog(id));
    }

    @Operation(summary = "新建流程草稿（版本1）")
    @PostMapping
    @PreAuthorize("@perm.has('process:def:edit')")
    public R<Long> create(@Valid @RequestBody ProcessDefUpsertRequest request) {
        return R.ok(processDefService.create(request));
    }

    @Operation(summary = "编辑流程草稿（已发布版本不可改）")
    @PutMapping
    @PreAuthorize("@perm.has('process:def:edit')")
    public R<Void> update(@Valid @RequestBody ProcessDefUpsertRequest request) {
        processDefService.update(request);
        return R.ok();
    }

    @Operation(summary = "基于某版本创建同编码新草稿版本")
    @PostMapping("/{id}/revise")
    @PreAuthorize("@perm.has('process:def:edit')")
    public R<Long> revise(@PathVariable Long id) {
        return R.ok(processDefService.revise(id));
    }

    @Operation(summary = "节点编排保存（仅草稿，九节点必须齐全）")
    @PutMapping("/{id}/nodes")
    @PreAuthorize("@perm.has('process:node:edit')")
    public R<Void> saveNodes(@PathVariable Long id, @Valid @RequestBody NodeBatchSaveRequest request) {
        processDefService.saveNodes(id, request);
        return R.ok();
    }

    @Operation(summary = "发布流程（旧发布版自动归档，写变更diff）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@perm.has('process:def:publish')")
    public R<Void> publish(@PathVariable Long id) {
        processDefService.publish(id);
        return R.ok();
    }
}
