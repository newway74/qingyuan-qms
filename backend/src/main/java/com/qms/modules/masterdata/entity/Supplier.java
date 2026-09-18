package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_supplier")
public class Supplier extends BaseEntity {

    private Long id;

    private String supplierCode;

    private String supplierName;

    private String contact;

    private String phone;

    private String address;

    /** QUALIFIED 合格 / CONTROLLED 受控 / DISABLED 禁用 */
    private String status;

    /** USER 用户数据 / DEMO 脱敏演示数据（一键清除演示数据仅删 DEMO） */
    private String dataSource;

    @Version
    private Integer lockVersion;
}
