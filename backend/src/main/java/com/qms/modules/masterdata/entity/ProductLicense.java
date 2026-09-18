package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 产品资质
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_product_license")
public class ProductLicense extends BaseEntity {

    private Long id;

    private Long productId;

    private String licenseType;

    private String certNo;

    private LocalDate validTo;

    private Long fileAttachmentId;
}
