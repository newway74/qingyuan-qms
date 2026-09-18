package com.qms.modules.masterdata.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 产品列表行（SPU + 品类/供应商名称回填）
 */
@Data
public class ProductListVO {

    private Long id;
    private String spuCode;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private String brand;
    private Long supplierId;
    private String supplierName;
    private String executionStandard;
    private String storageCondition;
    private Integer shelfLifeDays;
    private Integer extInspectionRequired;
    private String qualityStatus;
    private Integer lockVersion;

    /** 列表汇总用：SKU 数 */
    private Long skuCount;

    /** 列表汇总用：标示净含量描述（取首个启用 SKU，可空） */
    private BigDecimal firstNetContent;
    private String firstNetContentUnit;
    private String firstPackageForm;
}
