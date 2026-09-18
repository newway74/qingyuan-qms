package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品全流程实例：一个商品一条，记录其建档时绑定的模板版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_ledger_flow")
public class LedgerFlow extends BaseEntity {

    private Long id;

    private Long goodsId;

    private Long templateId;

    private Integer templateVersion;
}
