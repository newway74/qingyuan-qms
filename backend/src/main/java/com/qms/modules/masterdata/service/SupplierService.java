package com.qms.modules.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qms.common.exception.BizException;
import com.qms.common.result.PageRequest;
import com.qms.common.result.PageResult;
import com.qms.common.result.ResultCode;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.audit.AuditContext;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.masterdata.dto.SupplierLicenseRequest;
import com.qms.modules.masterdata.dto.SupplierUpsertRequest;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.entity.SupplierLicense;
import com.qms.modules.masterdata.entity.SupplierQuality;
import com.qms.modules.masterdata.mapper.SupplierLicenseMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.masterdata.mapper.SupplierQualityMapper;
import com.qms.modules.masterdata.vo.SupplierDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 供应商主数据服务。供应商不提供物理删除（以 DISABLED 状态停用并留痕）；证照逻辑删。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierService {

    /** 合法供应商状态 */
    private static final Set<String> SUPPLIER_STATUS = Set.of("QUALIFIED", "CONTROLLED", "DISABLED");

    private final SupplierMapper supplierMapper;
    private final SupplierLicenseMapper licenseMapper;
    private final SupplierQualityMapper qualityMapper;
    private final ObjectMapper objectMapper;

    public PageResult<Supplier> page(PageRequest request, String name, String code, String status) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .like(name != null && !name.isBlank(), Supplier::getSupplierName, name)
                .like(code != null && !code.isBlank(), Supplier::getSupplierCode, code)
                .eq(status != null && !status.isBlank(), Supplier::getStatus, status)
                .orderByDesc(Supplier::getId);
        Page<Supplier> page = supplierMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
        return PageResult.of(page);
    }

    public SupplierDetailVO detail(Long id) {
        Supplier supplier = getRequired(id);
        SupplierDetailVO vo = new SupplierDetailVO();
        vo.setSupplier(supplier);
        vo.setLicenses(licenseMapper.selectList(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getSupplierId, id)
                .orderByDesc(SupplierLicense::getValidTo)));
        vo.setQualityRatings(qualityMapper.selectList(new LambdaQueryWrapper<SupplierQuality>()
                .eq(SupplierQuality::getSupplierId, id)
                .orderByDesc(SupplierQuality::getPeriod)));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商管理", action = "CREATE", bizType = "qc_supplier")
    public Long create(SupplierUpsertRequest request) {
        normalize(request);
        ensureCodeUnique(request.getSupplierCode(), null);
        Supplier supplier = new Supplier();
        apply(supplier, request);
        supplierMapper.insert(supplier);
        return supplier.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商管理", action = "UPDATE", bizType = "qc_supplier", bizIdExpr = "#request.id")
    public void update(SupplierUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "id不能为空");
        }
        normalize(request);
        Supplier existing = getRequired(request.getId());
        putBefore(existing);
        ensureCodeUnique(request.getSupplierCode(), request.getId());
        apply(existing, request);
        supplierMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商证照", action = "CREATE", bizType = "qc_supplier_license")
    public Long addLicense(Long supplierId, SupplierLicenseRequest request) {
        getRequired(supplierId);
        validateLicenseDate(request);
        SupplierLicense license = new SupplierLicense();
        applyLicense(license, supplierId, request);
        licenseMapper.insert(license);
        return license.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商证照", action = "UPDATE", bizType = "qc_supplier_license", bizIdExpr = "#licenseId")
    public void updateLicense(Long supplierId, Long licenseId, SupplierLicenseRequest request) {
        getRequired(supplierId);
        SupplierLicense existing = getLicenseRequired(licenseId, supplierId);
        putBefore(existing);
        validateLicenseDate(request);
        applyLicense(existing, supplierId, request);
        existing.setId(licenseId);
        licenseMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "供应商证照", action = "DELETE_LOGIC", bizType = "qc_supplier_license", bizIdExpr = "#licenseId")
    public void deleteLicense(Long supplierId, Long licenseId) {
        getRequired(supplierId);
        SupplierLicense existing = getLicenseRequired(licenseId, supplierId);
        putBefore(existing);
        licenseMapper.deleteById(licenseId);
    }

    // ------------------------------------------------------------------

    public Supplier getRequired(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在");
        }
        return supplier;
    }

    private void apply(Supplier supplier, SupplierUpsertRequest request) {
        supplier.setSupplierCode(request.getSupplierCode().trim());
        supplier.setSupplierName(request.getSupplierName().trim());
        supplier.setContact(trim(request.getContact()));
        supplier.setPhone(trim(request.getPhone()));
        supplier.setAddress(trim(request.getAddress()));
        supplier.setStatus(request.getStatus());
    }

    private void applyLicense(SupplierLicense license, Long supplierId, SupplierLicenseRequest request) {
        license.setSupplierId(supplierId);
        license.setLicenseType(request.getLicenseType().trim());
        license.setCertNo(trim(request.getCertNo()));
        license.setValidFrom(request.getValidFrom());
        license.setValidTo(request.getValidTo());
        license.setFileAttachmentId(request.getFileAttachmentId());
        license.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void normalize(SupplierUpsertRequest request) {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus("QUALIFIED");
        }
        if (!SUPPLIER_STATUS.contains(request.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "供应商状态非法，仅支持 QUALIFIED/CONTROLLED/DISABLED");
        }
    }

    private void validateLicenseDate(SupplierLicenseRequest request) {
        if (request.getValidFrom() != null && request.getValidTo() != null
                && request.getValidTo().isBefore(request.getValidFrom())) {
            throw new BizException(ResultCode.BIZ_DATE_INVALID, "证照到期日不能早于生效日");
        }
    }

    private SupplierLicense getLicenseRequired(Long licenseId, Long supplierId) {
        SupplierLicense license = licenseMapper.selectById(licenseId);
        if (license == null || !supplierId.equals(license.getSupplierId())) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "证照不存在");
        }
        return license;
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        Long count = supplierMapper.countByCodeIncludeDeleted(SecurityUtils.getTenantId(), code.trim(), excludeId);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "供应商编码已存在（含已删除记录，编码不可复用）");
        }
    }

    private void putBefore(Object entity) {
        try {
            AuditContext.putBefore(objectMapper.writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("供应商 before 快照序列化失败: {}", e.getMessage());
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
