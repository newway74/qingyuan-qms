package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品资料项定义（系统预置一套示例项，管理员可继续维护）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_material_item")
public class MaterialItem extends BaseEntity {

    private Long id;

    private String itemCode;

    private String itemName;

    private Integer sort;

    /** 1 系统预置 0 自建 */
    private Integer isPreset;

    /** 1 启用 0 停用 */
    private Integer status;
}
