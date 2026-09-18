package com.qms.modules.npi.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.npi.dto.ProjectActionRequest;
import com.qms.modules.npi.dto.ProjectUpsertRequest;
import com.qms.modules.npi.service.NpiProjectService;
import com.qms.modules.npi.vo.ProjectDetailVO;
import com.qms.modules.npi.vo.ProjectListVO;
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

@Tag(name = "新品引入项目")
@RestController
@RequestMapping("/api/v1/npi/projects")
@RequiredArgsConstructor
public class NpiProjectController {

    private final NpiProjectService projectService;

    @Operation(summary = "项目分页")
    @GetMapping
    @PreAuthorize("@perm.has('npi:project:list')")
    public R<PageResult<ProjectListVO>> page(PageRequest request,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String projectName) {
        return R.ok(projectService.page(request, status, projectName));
    }

    @Operation(summary = "项目详情（三档标准/评审/评估/验厂/外检/时间线/闸门）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('npi:project:view')")
    public R<ProjectDetailVO> detail(@PathVariable Long id) {
        return R.ok(projectService.detail(id));
    }

    @Operation(summary = "新品会立项")
    @PostMapping
    @PreAuthorize("@perm.has('npi:project:create')")
    public R<Long> create(@Valid @RequestBody ProjectUpsertRequest request) {
        return R.ok(projectService.create(request));
    }

    @Operation(summary = "编辑立项信息（仅草稿阶段）")
    @PutMapping
    @PreAuthorize("@perm.has('npi:project:edit')")
    public R<Void> update(@Valid @RequestBody ProjectUpsertRequest request) {
        projectService.update(request);
        return R.ok();
    }

    @Operation(summary = "阶段推进：SUBMIT_STD/APPROVE_STD/FIX_SUPPLIER/AUDIT_PASS/AUDIT_FAIL/"
            + "SEND_EXT/TEST_PASS/TEST_FAIL/RELEASE/TERMINATE/REJECT_STD")
    @PostMapping("/{id}/actions/{action}")
    @PreAuthorize("@perm.has('npi:project:stage')")
    public R<Void> act(@PathVariable Long id,
                       @PathVariable String action,
                       @RequestBody(required = false) ProjectActionRequest request) {
        projectService.act(id, action, request == null ? new ProjectActionRequest() : request);
        return R.ok();
    }
}
