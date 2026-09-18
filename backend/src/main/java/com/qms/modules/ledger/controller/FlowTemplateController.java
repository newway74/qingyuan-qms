package com.qms.modules.ledger.controller;

import com.qms.common.result.R;
import com.qms.modules.ledger.dto.FlowNodeBatchSaveRequest;
import com.qms.modules.ledger.dto.FlowTemplateUpsertRequest;
import com.qms.modules.ledger.entity.FlowTemplate;
import com.qms.modules.ledger.service.FlowTemplateService;
import com.qms.modules.ledger.vo.FlowTemplateDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "商品全流程模板配置")
@RestController
@RequestMapping("/api/v1/ledger/flow-templates")
@RequiredArgsConstructor
public class FlowTemplateController {

    private final FlowTemplateService templateService;

    @Operation(summary = "模板列表（每个逻辑模板返回最新版本）")
    @GetMapping
    @PreAuthorize("@perm.has('ledger:template:list')")
    public R<List<FlowTemplate>> list() {
        return R.ok(templateService.listLatest());
    }

    @Operation(summary = "启用模板下拉（商品建档选择）")
    @GetMapping("/enabled")
    @PreAuthorize("@perm.has('ledger:goods:view')")
    public R<List<FlowTemplate>> enabled() {
        return R.ok(templateService.listEnabledLatest());
    }

    @Operation(summary = "模板详情（节点 + 字段配置 + 绑定商品数）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('ledger:template:list')")
    public R<FlowTemplateDetailVO> detail(@PathVariable Long id) {
        return R.ok(templateService.detail(id));
    }

    @Operation(summary = "新建模板（空节点，随后编排节点）")
    @PostMapping
    @PreAuthorize("@perm.has('ledger:template:edit')")
    public R<Long> create(@Valid @RequestBody FlowTemplateUpsertRequest request) {
        return R.ok(templateService.create(request));
    }

    @Operation(summary = "编辑模板名称/备注/启停")
    @PutMapping
    @PreAuthorize("@perm.has('ledger:template:edit')")
    public R<Void> update(@Valid @RequestBody FlowTemplateUpsertRequest request) {
        templateService.updateBasic(request);
        return R.ok();
    }

    @Operation(summary = "整体保存节点编排（有变更自动生成新版本，旧版本保留给已建商品）")
    @PutMapping("/{id}/nodes")
    @PreAuthorize("@perm.has('ledger:template:edit')")
    public R<Long> saveNodes(@PathVariable Long id,
                             @Valid @RequestBody FlowNodeBatchSaveRequest request) {
        return R.ok(templateService.saveNodes(id, request));
    }

    @Operation(summary = "复制模板（含节点与字段）")
    @PostMapping("/{id}/copy")
    @PreAuthorize("@perm.has('ledger:template:edit')")
    public R<Long> copy(@PathVariable Long id,
                        @RequestParam(required = false) String newName) {
        return R.ok(templateService.copy(id, newName));
    }

    @Operation(summary = "删除模板（预置/已绑定商品禁删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ledger:template:edit')")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }
}
