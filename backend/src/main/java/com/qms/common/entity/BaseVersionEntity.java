package com.qms.common.entity;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 关键单据实体：带乐观锁 lock_version。
 * 注意：标准模板/流程定义上的业务版本号列名为 version，与本乐观锁不是同一个概念。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseVersionEntity extends BaseEntity {

    @Version
    private Integer lockVersion;
}
