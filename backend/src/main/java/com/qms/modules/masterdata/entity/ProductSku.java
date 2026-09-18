package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 产品 SKU（袋装/罐装/盒装等包装形态）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_product_sku")
public class ProductSku extends BaseEntity {

    private Long id;

    private Long productId;

    private String skuCode;

    /** 规格 */
    private String spec;

    /** 袋装/罐装/盒装 */
    private String packageForm;

    /** 标示净含量 */
    private BigDecimal netContent;

    private String netContentUnit;

    private String barcode;

    /** 1启用 0停用 */
    private Integer status;
}
