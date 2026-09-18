package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 送样评估评分维度项。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_npi_eval_item")
public class NpiEvalItem extends BaseEntity {

    private Long id;

    private Long evalId;

    /** QUALITY/PACKAGE/PRICE/DELIVERY/SERVICE */
    private String dimensionCode;

    private String dimensionName;

    private BigDecimal score;

    private BigDecimal weight;

    private String note;
}
