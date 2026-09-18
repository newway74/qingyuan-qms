package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品资料清单：每个商品按启用资料项生成一行。
 * 状态 READY 齐套 / MISSING 缺失 / PENDING 待确认；后两者计入重点缺口。
 * 佐证附件走统一附件服务（bizType=LEDGER_MATERIAL, bizId=本记录 id）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_goods_material")
public class GoodsMaterial extends BaseEntity {

    private Long id;

    private Long goodsId;

    private Long itemId;

    /** 资料项名称快照 */
    private String itemName;

    /** READY / MISSING / PENDING */
    private String status;

    private String remark;
}
