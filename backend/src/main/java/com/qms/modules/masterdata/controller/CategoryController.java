package com.qms.modules.masterdata.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.masterdata.dto.CategoryUpsertRequest;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.service.CategoryService;
import com.qms.modules.masterdata.vo.CategoryTreeVO;
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

@Tag(name = "品类管理（样例CRUD）")
@RestController
@RequestMapping("/api/v1/master/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "品类分页")
    @GetMapping
    @PreAuthorize("@perm.has('master:category:list')")
    public R<PageResult<Category>> page(PageRequest request,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) Integer status) {
        return R.ok(categoryService.page(request, name, code, status));
    }

    @Operation(summary = "品类树")
    @GetMapping("/tree")
    @PreAuthorize("@perm.has('master:category:list')")
    public R<List<CategoryTreeVO>> tree() {
        return R.ok(categoryService.tree());
    }

    @Operation(summary = "品类详情")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('master:category:list')")
    public R<Category> detail(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @Operation(summary = "新增品类")
    @PostMapping
    @PreAuthorize("@perm.has('master:category:create')")
    public R<Long> create(@Valid @RequestBody CategoryUpsertRequest request) {
        return R.ok(categoryService.create(request));
    }

    @Operation(summary = "编辑品类")
    @PutMapping
    @PreAuthorize("@perm.has('master:category:edit')")
    public R<Void> update(@Valid @RequestBody CategoryUpsertRequest request) {
        categoryService.update(request);
        return R.ok();
    }

    @Operation(summary = "删除品类（逻辑删除，审计留痕）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('master:category:delete')")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.logicDelete(id);
        return R.ok();
    }
}
