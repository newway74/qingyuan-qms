package com.qms.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private Long id;

    private Long parentId;

    /** 权限码，如 master:category:list */
    private String permCode;

    private String name;

    /** MENU/BUTTON/API */
    private String type;

    /** 前端路由路径（菜单类型） */
    private String path;

    private String icon;

    private Integer sort;
}
