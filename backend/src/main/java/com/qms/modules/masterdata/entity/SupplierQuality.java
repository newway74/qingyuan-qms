package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 供应商质量评级（周期快照，阶段4闭环时写入；此处只读）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_supplier_quality")
public class SupplierQuality extends BaseEntity {

    private Long id;

    private Long supplierId;

    /** 评级周期 yyyyMM */
    private String period;

    private Integer batchCount;

    /** 合格率% */
    private BigDecimal passRate;

    private Integer defectCount;

    private BigDecimal score;

    /** A/B/C/D */
    private String grade;
}
