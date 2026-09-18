package com.qms.modules.standard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * JJF1070 定量包装商品净含量允许短缺量分档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_net_content_tolerance")
public class NetContentTolerance extends BaseEntity {

    private Long id;

    /** 区间下限（不含） */
    private BigDecimal minQty;

    /** 区间上限（含），null 表示上不封顶 */
    private BigDecimal maxQty;

    /** PERCENT 百分比 / ABSOLUTE 绝对短缺量 */
    private String shortageType;

    private BigDecimal shortageValue;

    private String unitScope;

    private Integer sort;
}
