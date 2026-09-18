package com.qms.modules.masterdata.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.masterdata.dto.ProductLicenseRequest;
import com.qms.modules.masterdata.dto.ProductQualityStatusRequest;
import com.qms.modules.masterdata.dto.ProductSkuRequest;
import com.qms.modules.masterdata.dto.ProductUpsertRequest;
import com.qms.modules.masterdata.service.ProductService;
import com.qms.modules.masterdata.vo.ProductDetailVO;
import com.qms.modules.masterdata.vo.ProductListVO;
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

@Tag(name = "产品档案主数据")
@RestController
@RequestMapping("/api/v1/master/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "产品SPU分页")
    @GetMapping
    @PreAuthorize("@perm.has('master:product:list')")
    public R<PageResult<ProductListVO>> page(PageRequest request,
                                             @RequestParam(required = false) String productName,
                                             @RequestParam(required = false) String spuCode,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long supplierId,
                                             @RequestParam(required = false) String qualityStatus) {
        return R.ok(productService.page(request, productName, spuCode, categoryId, supplierId, qualityStatus));
    }

    @Operation(summary = "产品详情（含SKU与资质）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('master:product:list')")
    public R<ProductDetailVO> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @Operation(summary = "新增产品SPU")
    @PostMapping
    @PreAuthorize("@perm.has('master:product:create')")
    public R<Long> create(@Valid @RequestBody ProductUpsertRequest request) {
        return R.ok(productService.create(request));
    }

    @Operation(summary = "编辑产品SPU")
    @PutMapping
    @PreAuthorize("@perm.has('master:product:edit')")
    public R<Void> update(@Valid @RequestBody ProductUpsertRequest request) {
        productService.update(request);
        return R.ok();
    }

    @Operation(summary = "质量状态变更（受控/冻结/淘汰，强制填原因，留痕）")
    @PutMapping("/{id}/quality-status")
    @PreAuthorize("@perm.has('master:product:status')")
    public R<Void> changeQualityStatus(@PathVariable Long id,
                                       @Valid @RequestBody ProductQualityStatusRequest request) {
        productService.changeQualityStatus(id, request);
        return R.ok();
    }

    @Operation(summary = "新增SKU")
    @PostMapping("/{id}/skus")
    @PreAuthorize("@perm.has('master:product:sku')")
    public R<Long> addSku(@PathVariable("id") Long productId,
                          @Valid @RequestBody ProductSkuRequest request) {
        return R.ok(productService.addSku(productId, request));
    }

    @Operation(summary = "编辑SKU")
    @PutMapping("/{id}/skus/{skuId}")
    @PreAuthorize("@perm.has('master:product:sku')")
    public R<Void> updateSku(@PathVariable("id") Long productId,
                             @PathVariable Long skuId,
                             @Valid @RequestBody ProductSkuRequest request) {
        productService.updateSku(productId, skuId, request);
        return R.ok();
    }

    @Operation(summary = "删除SKU（逻辑删除，留痕）")
    @DeleteMapping("/{id}/skus/{skuId}")
    @PreAuthorize("@perm.has('master:product:sku')")
    public R<Void> deleteSku(@PathVariable("id") Long productId,
                             @PathVariable Long skuId) {
        productService.deleteSku(productId, skuId);
        return R.ok();
    }

    @Operation(summary = "新增产品资质")
    @PostMapping("/{id}/licenses")
    @PreAuthorize("@perm.has('master:product:edit')")
    public R<Long> addLicense(@PathVariable("id") Long productId,
                              @Valid @RequestBody ProductLicenseRequest request) {
        return R.ok(productService.addLicense(productId, request));
    }

    @Operation(summary = "编辑产品资质")
    @PutMapping("/{id}/licenses/{licenseId}")
    @PreAuthorize("@perm.has('master:product:edit')")
    public R<Void> updateLicense(@PathVariable("id") Long productId,
                                 @PathVariable Long licenseId,
                                 @Valid @RequestBody ProductLicenseRequest request) {
        productService.updateLicense(productId, licenseId, request);
        return R.ok();
    }

    @Operation(summary = "删除产品资质（逻辑删除，留痕）")
    @DeleteMapping("/{id}/licenses/{licenseId}")
    @PreAuthorize("@perm.has('master:product:edit')")
    public R<Void> deleteLicense(@PathVariable("id") Long productId,
                                 @PathVariable Long licenseId) {
        productService.deleteLicense(productId, licenseId);
        return R.ok();
    }
}
