package com.qms.modules.ledger.vo;

import lombok.Data;

/**
 * 资料项定义视图（管理员维护列表用）。
 */
@Data
public class MaterialItemVO {

    private Long id;
    private String itemCode;
    private String itemName;
    private Integer sort;
    /** 1 系统预置 0 自建（预置项可停用/改名，不可删除） */
    private Integer isPreset;
    /** 1 启用 0 停用 */
    private Integer status;
}
