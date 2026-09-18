package com.qms.modules.standard.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.standard.dto.GradeTemplateGenerateRequest;
import com.qms.modules.standard.dto.ItemBatchSaveRequest;
import com.qms.modules.standard.dto.StdReviewRequest;
import com.qms.modules.standard.dto.TemplateCopyRequest;
import com.qms.modules.standard.dto.TemplateUpsertRequest;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.entity.StdReview;
import com.qms.modules.standard.service.StandardTemplateService;
import com.qms.modules.standard.vo.TemplateDetailVO;
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

@Tag(name = "检验标准模板")
@RestController
@RequestMapping("/api/v1/standard/templates")
@RequiredArgsConstructor
public class StandardTemplateController {

    private final StandardTemplateService templateService;

    @Operation(summary = "模板分页（含全部版本行）")
    @GetMapping
    @PreAuthorize("@perm.has('std:template:list')")
    public R<PageResult<StandardTemplate>> page(PageRequest request,
                                                @RequestParam(required = false) String templateName,
                                                @RequestParam(required = false) String templateCode,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String grade,
                                                @RequestParam(required = false) Long npiProjectId) {
        return R.ok(templateService.page(request, templateName, templateCode, categoryId, status, grade, npiProjectId));
    }

    @Operation(summary = "当前生效模板（按品类+包装形态）")
    @GetMapping("/effective")
    @PreAuthorize("@perm.has('std:template:list')")
    public R<TemplateDetailVO> effective(@RequestParam Long categoryId,
                                         @RequestParam(required = false) String packageForm) {
        return R.ok(templateService.effective(categoryId, packageForm));
    }

    @Operation(summary = "模板详情（含检验项）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('std:template:list')")
    public R<TemplateDetailVO> detail(@PathVariable Long id) {
        return R.ok(templateService.detail(id));
    }

    @Operation(summary = "版本列表")
    @GetMapping("/{id}/versions")
    @PreAuthorize("@perm.has('std:template:list')")
    public R<List<StandardTemplate>> versions(@PathVariable Long id) {
        return R.ok(templateService.versions(id));
    }

    @Operation(summary = "检验项列表")
    @GetMapping("/{id}/items")
    @PreAuthorize("@perm.has('std:template:list')")
    public R<List<StandardItem>> items(@PathVariable Long id) {
        return R.ok(templateService.detail(id).getItems());
    }

    @Operation(summary = "新建草稿（版本1）")
    @PostMapping
    @PreAuthorize("@perm.has('std:template:edit')")
    public R<Long> create(@Valid @RequestBody TemplateUpsertRequest request) {
        return R.ok(templateService.create(request));
    }

    @Operation(summary = "编辑草稿头信息（已发布版本不可改）")
    @PutMapping
    @PreAuthorize("@perm.has('std:template:edit')")
    public R<Void> update(@Valid @RequestBody TemplateUpsertRequest request) {
        templateService.update(request);
        return R.ok();
    }

    @Operation(summary = "基于某版本创建同编码新草稿版本")
    @PostMapping("/{id}/revise")
    @PreAuthorize("@perm.has('std:template:edit')")
    public R<Long> revise(@PathVariable Long id) {
        return R.ok(templateService.revise(id));
    }

    @Operation(summary = "复制为新编码的草稿（含检验项）")
    @PostMapping("/{id}/copy")
    @PreAuthorize("@perm.has('std:template:edit')")
    public R<Long> copy(@PathVariable Long id, @Valid @RequestBody TemplateCopyRequest request) {
        return R.ok(templateService.copy(id, request));
    }

    @Operation(summary = "发布草稿（旧发布版自动归档）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@perm.has('std:template:publish')")
    public R<Void> publish(@PathVariable Long id) {
        templateService.publish(id);
        return R.ok();
    }

    @Operation(summary = "新品项目下的档位标准列表")
    @GetMapping("/by-project/{projectId}")
    @PreAuthorize("@perm.has('npi:project:view')")
    public R<List<StandardTemplate>> byProject(@PathVariable Long projectId) {
        return R.ok(templateService.listByProject(projectId));
    }

    @Operation(summary = "标准评审记录")
    @GetMapping("/{id}/reviews")
    @PreAuthorize("@perm.has('std:template:list')")
    public R<List<StdReview>> reviews(@PathVariable Long id) {
        return R.ok(templateService.reviews(id));
    }

    @Operation(summary = "新品项目：依据品类现行标准生成档位草稿（含检验项）")
    @PostMapping("/grade/generate")
    @PreAuthorize("@perm.has('std:template:edit')")
    public R<Long> generateGrade(@Valid @RequestBody GradeTemplateGenerateRequest request) {
        return R.ok(templateService.generateGradeTemplate(request));
    }

    @Operation(summary = "提交评审（采购会签/老板批准流）")
    @PostMapping("/{id}/review/submit")
    @PreAuthorize("@perm.has('std:template:review')")
    public R<Void> submitReview(@PathVariable Long id) {
        templateService.submitReview(id);
        return R.ok();
    }

    @Operation(summary = "评审动作（PROCUREMENT 采购会签 / BOSS 老板批准，可驳回）")
    @PostMapping("/{id}/review")
    @PreAuthorize("@perm.has('std:template:review')")
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody StdReviewRequest request) {
        templateService.review(id, request);
        return R.ok();
    }

    @Operation(summary = "检验项批量保存（仅草稿，整组替换，留痕）")
    @PutMapping("/{id}/items")
    @PreAuthorize("@perm.has('std:template:edit')")
    public R<Void> saveItems(@PathVariable Long id, @Valid @RequestBody ItemBatchSaveRequest request) {
        templateService.saveItems(id, request);
        return R.ok();
    }
}
