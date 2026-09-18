package com.qms.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private Long id;

    /** SAMPLER/INSPECTOR/REVIEWER/QA_MANAGER/ADMIN */
    private String code;

    private String name;

    /** ALL/CATEGORY/BRAND/DEPT */
    private String dataScope;

    private Integer status;

    private String remark;
}
