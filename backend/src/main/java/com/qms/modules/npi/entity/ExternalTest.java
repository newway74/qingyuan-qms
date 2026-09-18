package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 第三方/国家检测机构送检单（上市放行前置）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_external_test")
public class ExternalTest extends BaseEntity {

    private Long id;

    private String testNo;

    private Long projectId;

    private Long supplierId;

    private String productName;

    private String sampleDesc;

    private String labName;

    /** CMA/CNAS/NMPA/OTHER */
    private String labQualification;

    private String testItems;

    private LocalDateTime sentAt;

    private String reportNo;

    private LocalDate reportDate;

    /** PENDING/PASS/FAIL/PARTIAL */
    private String conclusion;

    private Long reportAttachmentId;

    /** PLANNED/SENT/REPORTED */
    private String status;

    private String remark;

    @Version
    private Integer lockVersion;
}
