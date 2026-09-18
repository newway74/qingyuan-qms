package com.qms.modules.npi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 验厂单新建/评分保存/结论确认。 */
@Data
public class AuditUpsertRequest {

    private Long id;

    @NotNull
    private Long projectId;

    @NotNull
    private Long supplierId;

    private Long parentAuditId;

    /** INITIAL/FOLLOW_UP，默认 INITIAL */
    private String auditType;

    private LocalDateTime plannedAt;

    private LocalDateTime auditedAt;

    private String auditors;

    private String rectifyRequirement;

    private LocalDate rectifyDeadline;

    private String remark;

    /** 确认验厂结论（PASS/CONDITIONAL/FAIL）；传入时校验评分并置 CONFIRMED */
    private String confirmConclusion;

    @Valid
    private List<AuditItemInput> items;
}
