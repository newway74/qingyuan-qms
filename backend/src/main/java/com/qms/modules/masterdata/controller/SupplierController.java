package com.qms.modules.masterdata.controller;

import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.R;
import com.qms.modules.masterdata.dto.SupplierLicenseRequest;
import com.qms.modules.masterdata.dto.SupplierUpsertRequest;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.entity.SupplierQuality;
import com.qms.modules.masterdata.service.SupplierQualityService;
import com.qms.modules.masterdata.service.SupplierService;
import com.qms.modules.masterdata.vo.SupplierDetailVO;
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

@Tag(name = "供应商主数据")
@RestController
@RequestMapping("/api/v1/master/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierQualityService supplierQualityService;

    @Operation(summary = "供应商分页")
    @GetMapping
    @PreAuthorize("@perm.has('master:supplier:list')")
    public R<PageResult<Supplier>> page(PageRequest request,
                                        @RequestParam(required = false) String supplierName,
                                        @RequestParam(required = false) String supplierCode,
                                        @RequestParam(required = false) String status) {
        return R.ok(supplierService.page(request, supplierName, supplierCode, status));
    }

    @Operation(summary = "供应商详情（含证照与评级历史）")
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('master:supplier:list')")
    public R<SupplierDetailVO> detail(@PathVariable Long id) {
        return R.ok(supplierService.detail(id));
    }

    @Operation(summary = "供应商质量评级历史")
    @GetMapping("/{id}/quality")
    @PreAuthorize("@perm.has('master:supplier:list')")
    public R<List<SupplierQuality>> quality(@PathVariable Long id) {
        return R.ok(supplierService.detail(id).getQualityRatings());
    }

    @Operation(summary = "供应商质量评级趋势（按期升序）")
    @GetMapping("/{id}/quality-trend")
    @PreAuthorize("@perm.has('master:supplier:list')")
    public R<List<SupplierQuality>> qualityTrend(@PathVariable Long id) {
        return R.ok(supplierQualityService.trend(id));
    }

    @Operation(summary = "新增供应商")
    @PostMapping
    @PreAuthorize("@perm.has('master:supplier:create')")
    public R<Long> create(@Valid @RequestBody SupplierUpsertRequest request) {
        return R.ok(supplierService.create(request));
    }

    @Operation(summary = "编辑供应商")
    @PutMapping
    @PreAuthorize("@perm.has('master:supplier:edit')")
    public R<Void> update(@Valid @RequestBody SupplierUpsertRequest request) {
        supplierService.update(request);
        return R.ok();
    }

    @Operation(summary = "新增供应商证照")
    @PostMapping("/{id}/licenses")
    @PreAuthorize("@perm.has('master:supplier:license')")
    public R<Long> addLicense(@PathVariable("id") Long supplierId,
                              @Valid @RequestBody SupplierLicenseRequest request) {
        return R.ok(supplierService.addLicense(supplierId, request));
    }

    @Operation(summary = "编辑供应商证照")
    @PutMapping("/{id}/licenses/{licenseId}")
    @PreAuthorize("@perm.has('master:supplier:license')")
    public R<Void> updateLicense(@PathVariable("id") Long supplierId,
                                 @PathVariable Long licenseId,
                                 @Valid @RequestBody SupplierLicenseRequest request) {
        supplierService.updateLicense(supplierId, licenseId, request);
        return R.ok();
    }

    @Operation(summary = "删除供应商证照（逻辑删除，留痕）")
    @DeleteMapping("/{id}/licenses/{licenseId}")
    @PreAuthorize("@perm.has('master:supplier:license')")
    public R<Void> deleteLicense(@PathVariable("id") Long supplierId,
                                 @PathVariable Long licenseId) {
        supplierService.deleteLicense(supplierId, licenseId);
        return R.ok();
    }
}
