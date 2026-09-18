package com.qms.modules.npi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 外检送检单：新建/寄出登记/报告回填共用。 */
@Data
public class ExtTestUpsertRequest {

    private Long id;

    @NotNull
    private Long projectId;

    @NotNull
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

    private String remark;
}
