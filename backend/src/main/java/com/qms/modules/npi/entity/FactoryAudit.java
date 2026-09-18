package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 供应商实地验厂单（生产/仓储/冷链合规评估）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_factory_audit")
public class FactoryAudit extends BaseEntity {

    private Long id;

    private String auditNo;

    private Long projectId;

    private Long supplierId;

    private Long parentAuditId;

    /** INITIAL/FOLLOW_UP */
    private String auditType;

    private LocalDateTime plannedAt;

    private LocalDateTime auditedAt;

    private Long leaderId;

    private String auditors;

    private BigDecimal totalScore;

    /** PASS/CONDITIONAL/FAIL */
    private String conclusion;

    private String rectifyRequirement;

    private LocalDate rectifyDeadline;

    private Long reportAttachmentId;

    /** PLANNED/IN_PROGRESS/SUBMITTED/CONFIRMED */
    private String status;

    private String remark;

    @Version
    private Integer lockVersion;
}
