package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新品引入项目（新品会立项 → 标准评审 → 寻源送样 → 验厂 → 量产 → 外检 → 上市放行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_npi_project")
public class NpiProject extends BaseEntity {

    private Long id;

    private String projectNo;

    private String projectName;

    private Long categoryId;

    private String brand;

    private String background;

    private LocalDateTime meetingAt;

    private String attendees;

    private Long initiatorId;

    private LocalDate targetListingDate;

    /** DRAFT/STD_REVIEW/SOURCING/AUDIT/PRODUCING/EXT_TEST/LISTING_REVIEW/LISTED/TERMINATED */
    private String status;

    /** HIGH/MID/LOW */
    private String targetGrade;

    private Long selectedTemplateId;

    private Long chosenSupplierId;

    private Long productId;

    private LocalDateTime stdSubmittedAt;

    private LocalDateTime stdApprovedAt;

    private LocalDateTime supplierFixedAt;

    private LocalDateTime auditPassedAt;

    private LocalDateTime extTestPassedAt;

    private LocalDateTime listedAt;

    private String terminateReason;

    @Version
    private Integer lockVersion;
}
