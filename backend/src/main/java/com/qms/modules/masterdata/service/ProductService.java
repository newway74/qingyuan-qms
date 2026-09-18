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
import com.qms.modules.masterdata.dto.ProductLicenseRequest;
import com.qms.modules.masterdata.dto.ProductQualityStatusRequest;
import com.qms.modules.masterdata.dto.ProductSkuRequest;
import com.qms.modules.masterdata.dto.ProductUpsertRequest;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.entity.Product;
import com.qms.modules.masterdata.entity.ProductLicense;
import com.qms.modules.masterdata.entity.ProductSku;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.masterdata.mapper.ProductLicenseMapper;
import com.qms.modules.masterdata.mapper.ProductMapper;
import com.qms.modules.masterdata.mapper.ProductSkuMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import com.qms.modules.masterdata.vo.ProductDetailVO;
import com.qms.modules.masterdata.vo.ProductListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品档案服务：SPU 不物理删除（以 DISABLED 淘汰并全程留痕）；SKU/资质逻辑删。
 * 质量状态变更必须填原因，且走乐观锁防并发覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> QUALITY_STATUS = Set.of("NORMAL", "CONTROLLED", "FROZEN", "DISABLED");

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductLicenseMapper licenseMapper;
    private final CategoryMapper categoryMapper;
    private final SupplierMapper supplierMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ProductListVO> page(PageRequest request, String name, String spuCode,
                                          Long categoryId, Long supplierId, String qualityStatus) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(name != null && !name.isBlank(), Product::getProductName, name)
                .like(spuCode != null && !spuCode.isBlank(), Product::getSpuCode, spuCode)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(supplierId != null, Product::getSupplierId, supplierId)
                .eq(qualityStatus != null && !qualityStatus.isBlank(), Product::getQualityStatus, qualityStatus)
                .orderByDesc(Product::getId);
        Page<Product> page = productMapper.selectPage(new Page<>(request.getPageNo(), request.getPageSize()), wrapper);

        List<Product> records = page.getRecords();
        Map<Long, String> categoryNames = loadCategoryNames(records);
        Map<Long, String> supplierNames = loadSupplierNames(records);
        Map<Long, List<ProductSku>> skuMap = loadSkus(records.stream().map(Product::getId).toList());

        List<ProductListVO> voList = records.stream().map(p -> {
            ProductListVO vo = new ProductListVO();
            vo.setId(p.getId());
            vo.setSpuCode(p.getSpuCode());
            vo.setProductName(p.getProductName());
            vo.setCategoryId(p.getCategoryId());
            vo.setCategoryName(categoryNames.get(p.getCategoryId()));
            vo.setBrand(p.getBrand());
            vo.setSupplierId(p.getSupplierId());
            vo.setSupplierName(p.getSupplierId() == null ? null : supplierNames.get(p.getSupplierId()));
            vo.setExecutionStandard(p.getExecutionStandard());
            vo.setStorageCondition(p.getStorageCondition());
            vo.setShelfLifeDays(p.getShelfLifeDays());
            vo.setExtInspectionRequired(p.getExtInspectionRequired());
            vo.setQualityStatus(p.getQualityStatus());
            vo.setLockVersion(p.getLockVersion());
            List<ProductSku> skus = skuMap.getOrDefault(p.getId(), Collections.emptyList());
            vo.setSkuCount((long) skus.size());
            skus.stream().filter(s -> s.getStatus() != null && s.getStatus() == 1).findFirst().ifPresent(first -> {
                vo.setFirstNetContent(first.getNetContent());
                vo.setFirstNetContentUnit(first.getNetContentUnit());
                vo.setFirstPackageForm(first.getPackageForm());
            });
            return vo;
        }).toList();

        return PageResult.of(page, voList);
    }

    public ProductDetailVO detail(Long id) {
        Product product = getRequired(id);
        ProductDetailVO vo = new ProductDetailVO();
        vo.setProduct(product);
        Category category = categoryMapper.selectById(product.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        if (product.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectById(product.getSupplierId());
            vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());
        }
        vo.setSkus(skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, id)
                .orderByAsc(ProductSku::getId)));
        vo.setLicenses(licenseMapper.selectList(new LambdaQueryWrapper<ProductLicense>()
                .eq(ProductLicense::getProductId, id)
                .orderByDesc(ProductLicense::getValidTo)));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品档案", action = "CREATE", bizType = "qc_product")
    public Long create(ProductUpsertRequest request) {
        normalize(request);
        validateRelations(request);
        ensureSpuCodeUnique(request.getSpuCode(), null);
        Product product = new Product();
        apply(product, request);
        productMapper.insert(product);
        return product.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品档案", action = "UPDATE", bizType = "qc_product", bizIdExpr = "#request.id")
    public void update(ProductUpsertRequest request) {
        if (request.getId() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "id不能为空");
        }
        normalize(request);
        Product existing = getRequired(request.getId());
        putBefore(existing);
        validateRelations(request);
        ensureSpuCodeUnique(request.getSpuCode(), request.getId());
        apply(existing, request);
        int rows = productMapper.updateById(existing);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品质量状态", action = "APPROVE", bizType = "qc_product", bizIdExpr = "#id")
    public void changeQualityStatus(Long id, ProductQualityStatusRequest request) {
        if (!QUALITY_STATUS.contains(request.getQualityStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "质量状态非法，仅支持 NORMAL/CONTROLLED/FROZEN/DISABLED");
        }
        Product existing = getRequired(id);
        putBefore(existing);
        if (request.getQualityStatus().equals(existing.getQualityStatus())) {
            throw new BizException(ResultCode.BIZ_STATE_INVALID, "产品当前已是该质量状态");
        }
        existing.setQualityStatus(request.getQualityStatus());
        // 前端携带版本号时按其校验并发（DTO 中的原因会进入审计 afterValue，完成留痕）
        if (request.getLockVersion() != null) {
            existing.setLockVersion(request.getLockVersion());
        }
        int rows = productMapper.updateById(existing);
        if (rows == 0) {
            throw new BizException(ResultCode.CONCURRENT_VERSION_CONFLICT);
        }
    }

    // ---------------- SKU ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品SKU", action = "CREATE", bizType = "qc_product_sku")
    public Long addSku(Long productId, ProductSkuRequest request) {
        getRequired(productId);
        ensureSkuCodeUnique(request.getSkuCode(), null);
        ProductSku sku = new ProductSku();
        applySku(sku, productId, request);
        skuMapper.insert(sku);
        return sku.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品SKU", action = "UPDATE", bizType = "qc_product_sku", bizIdExpr = "#skuId")
    public void updateSku(Long productId, Long skuId, ProductSkuRequest request) {
        getRequired(productId);
        ProductSku existing = getSkuRequired(skuId, productId);
        putBefore(existing);
        ensureSkuCodeUnique(request.getSkuCode(), skuId);
        applySku(existing, productId, request);
        existing.setId(skuId);
        skuMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品SKU", action = "DELETE_LOGIC", bizType = "qc_product_sku", bizIdExpr = "#skuId")
    public void deleteSku(Long productId, Long skuId) {
        getRequired(productId);
        ProductSku existing = getSkuRequired(skuId, productId);
        putBefore(existing);
        skuMapper.deleteById(skuId);
    }

    // ---------------- 产品资质 ----------------

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品资质", action = "CREATE", bizType = "qc_product_license")
    public Long addLicense(Long productId, ProductLicenseRequest request) {
        getRequired(productId);
        ProductLicense license = new ProductLicense();
        applyLicense(license, productId, request);
        licenseMapper.insert(license);
        return license.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品资质", action = "UPDATE", bizType = "qc_product_license", bizIdExpr = "#licenseId")
    public void updateLicense(Long productId, Long licenseId, ProductLicenseRequest request) {
        getRequired(productId);
        ProductLicense existing = getLicenseRequired(licenseId, productId);
        putBefore(existing);
        applyLicense(existing, productId, request);
        existing.setId(licenseId);
        licenseMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    @AuditLog(module = "产品资质", action = "DELETE_LOGIC", bizType = "qc_product_license", bizIdExpr = "#licenseId")
    public void deleteLicense(Long productId, Long licenseId) {
        getRequired(productId);
        ProductLicense existing = getLicenseRequired(licenseId, productId);
        putBefore(existing);
        licenseMapper.deleteById(licenseId);
    }

    // ------------------------------------------------------------------

    public Product getRequired(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "产品不存在");
        }
        return product;
    }

    private void apply(Product product, ProductUpsertRequest request) {
        product.setSpuCode(request.getSpuCode().trim());
        product.setProductName(request.getProductName().trim());
        product.setCategoryId(request.getCategoryId());
        product.setBrand(trim(request.getBrand()));
        product.setSupplierId(request.getSupplierId());
        product.setExecutionStandard(trim(request.getExecutionStandard()));
        product.setStorageCondition(trim(request.getStorageCondition()));
        product.setShelfLifeDays(request.getShelfLifeDays());
        product.setExtInspectionRequired(request.getExtInspectionRequired() == null ? 0 : request.getExtInspectionRequired());
        product.setQualityStatus(request.getQualityStatus());
    }

    private void applySku(ProductSku sku, Long productId, ProductSkuRequest request) {
        sku.setProductId(productId);
        sku.setSkuCode(request.getSkuCode().trim());
        sku.setSpec(trim(request.getSpec()));
        sku.setPackageForm(trim(request.getPackageForm()));
        sku.setNetContent(request.getNetContent());
        sku.setNetContentUnit(request.getNetContentUnit() == null || request.getNetContentUnit().isBlank()
                ? "g" : request.getNetContentUnit().trim());
        sku.setBarcode(trim(request.getBarcode()));
        sku.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void applyLicense(ProductLicense license, Long productId, ProductLicenseRequest request) {
        license.setProductId(productId);
        license.setLicenseType(request.getLicenseType().trim());
        license.setCertNo(trim(request.getCertNo()));
        license.setValidTo(request.getValidTo());
        license.setFileAttachmentId(request.getFileAttachmentId());
    }

    private void normalize(ProductUpsertRequest request) {
        if (request.getQualityStatus() == null || request.getQualityStatus().isBlank()) {
            request.setQualityStatus("NORMAL");
        }
        if (!QUALITY_STATUS.contains(request.getQualityStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "质量状态非法，仅支持 NORMAL/CONTROLLED/FROZEN/DISABLED");
        }
    }

    /** 外键合法性在服务端兜底校验，防止绕过前端写入脏数据 */
    private void validateRelations(ProductUpsertRequest request) {
        if (categoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "所选品类不存在");
        }
        if (request.getSupplierId() != null && supplierMapper.selectById(request.getSupplierId()) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "所选供应商不存在");
        }
    }

    private ProductSku getSkuRequired(Long skuId, Long productId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SKU不存在");
        }
        return sku;
    }

    private ProductLicense getLicenseRequired(Long licenseId, Long productId) {
        ProductLicense license = licenseMapper.selectById(licenseId);
        if (license == null || !productId.equals(license.getProductId())) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "产品资质不存在");
        }
        return license;
    }

    private void ensureSpuCodeUnique(String code, Long excludeId) {
        Long count = productMapper.countByCodeIncludeDeleted(SecurityUtils.getTenantId(), code.trim(), excludeId);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "SPU编码已存在（含已删除记录，编码不可复用）");
        }
    }

    private void ensureSkuCodeUnique(String code, Long excludeId) {
        Long count = skuMapper.countByCodeIncludeDeleted(SecurityUtils.getTenantId(), code.trim(), excludeId);
        if (count != null && count > 0) {
            throw new BizException(ResultCode.DATA_DUPLICATED, "SKU编码已存在（含已删除记录，编码不可复用）");
        }
    }

    private Map<Long, String> loadCategoryNames(List<Product> products) {
        Set<Long> ids = products.stream().map(Product::getCategoryId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private Map<Long, String> loadSupplierNames(List<Product> products) {
        Set<Long> ids = products.stream().map(Product::getSupplierId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return supplierMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName));
    }

    private Map<Long, List<ProductSku>> loadSkus(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getProductId, productIds)
                .orderByAsc(ProductSku::getId));
        Map<Long, List<ProductSku>> map = new HashMap<>();
        for (ProductSku sku : skus) {
            map.computeIfAbsent(sku.getProductId(), k -> new java.util.ArrayList<>()).add(sku);
        }
        return map;
    }

    private void putBefore(Object entity) {
        try {
            AuditContext.putBefore(objectMapper.writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("产品 before 快照序列化失败: {}", e.getMessage());
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
