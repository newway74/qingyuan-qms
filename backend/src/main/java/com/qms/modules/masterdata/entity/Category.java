package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品类（中药饮片/滋补食材/花茶/健康食品……），树形
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_category")
public class Category extends BaseEntity {

    private Long id;

    private Long parentId;

    private String code;

    private String name;

    private Integer sort;

    /** 1启用 0停用 */
    private Integer status;
}
