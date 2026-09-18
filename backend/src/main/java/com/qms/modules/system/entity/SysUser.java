package com.qms.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private Long id;

    private Long deptId;

    private String username;

    private String realName;

    private String passwordHash;

    private String phone;

    private String email;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    // ====== 非数据库字段 ======

    @TableField(exist = false)
    private List<String> roles;

    @TableField(exist = false)
    private List<String> permissions;

    @TableField(exist = false)
    private String deptName;

    /** 数据范围：ALL/CATEGORY/BRAND/DEPT（取角色最宽范围） */
    @TableField(exist = false)
    private String dataScope;
}
