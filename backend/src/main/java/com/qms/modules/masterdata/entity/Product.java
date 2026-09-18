package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品档案 SPU
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_product")
public class Product extends BaseEntity {

    private Long id;

    private String spuCode;

    private String productName;

    private Long categoryId;

    private String brand;

    private Long supplierId;

    /** 执行标准号 */
    private String executionStandard;

    /** 储存条件 */
    private String storageCondition;

    /** 保质期天数 */
    private Integer shelfLifeDays;

    /** 是否需第三方外检 1是 0否 */
    private Integer extInspectionRequired;

    /** NORMAL 正常 / CONTROLLED 受控 / FROZEN 冻结 / DISABLED 淘汰禁用 */
    private String qualityStatus;

    @Version
    private Integer lockVersion;
}
