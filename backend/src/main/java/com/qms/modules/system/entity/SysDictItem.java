package com.qms.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {

    private Long id;
    private String typeCode;
    private String itemValue;
    private String itemLabel;
    private Integer sort;
    private Integer status;
    private String extra;
}
