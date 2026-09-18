package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 验厂检查项（资质/生产/仓储/冷链/质量体系）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_factory_audit_item")
public class FactoryAuditItem extends BaseEntity {

    private Long id;

    private Long auditId;

    /** QUALIFICATION/PRODUCTION/WAREHOUSE/COLD_CHAIN/QUALITY_SYSTEM */
    private String category;

    private String itemName;

    private String requirement;

    private BigDecimal maxScore;

    private BigDecimal score;

    /** COMPLIANT/MINOR/MAJOR/NA */
    private String result;

    private String note;

    private Integer sort;
}
