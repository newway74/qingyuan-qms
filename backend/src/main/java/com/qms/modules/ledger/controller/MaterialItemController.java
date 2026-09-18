package com.qms.modules.ledger.controller;

import com.qms.common.result.R;
import com.qms.modules.ledger.dto.MaterialItemUpsertRequest;
import com.qms.modules.ledger.service.MaterialService;
import com.qms.modules.ledger.vo.MaterialItemVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资料项定义维护（仅管理员）：新建/改名/排序/启停/删除。
 * 新建或启用后自动把资料项补挂到全部既有商品，不覆盖已维护状态。
 */
@Tag(name = "商品品控台账-资料项维护")
@RestController
@RequestMapping("/api/v1/ledger/material-items")
@RequiredArgsConstructor
public class MaterialItemController {

    private final MaterialService materialService;

    @Operation(summary = "资料项列表（含停用，管理员维护用）")
    @GetMapping
    @PreAuthorize("@perm.has('ledger:template:list')")
    public R<List<MaterialItemVO>> list() {
        return R.ok(materialService.listAllItems());
    }

    @Operation(summary = "新建资料项（自动补挂全部商品）")
    @PostMapping
    @PreAuthorize("@perm.has('ledger:material:edit')")
    public R<Long> create(@Valid @RequestBody MaterialItemUpsertRequest request) {
        return R.ok(materialService.createItem(request));
    }

    @Operation(summary = "编辑资料项（改名/排序/启停，重新启用自动补挂）")
    @PutMapping
    @PreAuthorize("@perm.has('ledger:material:edit')")
    public R<Void> update(@Valid @RequestBody MaterialItemUpsertRequest request) {
        materialService.updateItem(request);
        return R.ok();
    }

    @Operation(summary = "删除资料项（预置项禁删，可停用；商品历史清单保留）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('ledger:material:edit')")
    public R<Void> delete(@PathVariable Long id) {
        materialService.deleteItem(id);
        return R.ok();
    }
}
