package com.qms.modules.sampling.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 抽样单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_sampling")
public class Sampling extends BaseEntity {

    private Long id;

    private String samplingNo;

    /** NEW_ADMISSION/INCOMING_BATCH/PERIODIC/COMPLAINT/FLYING */
    private String source;

    private Long skuId;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    private String storageCondition;

    private BigDecimal sampleQuantity;

    private String quantityUnit;

    private String samplingLocation;

    private Long samplerId;

    private LocalDateTime sampledAt;

    private String remark;

    /** 撤销原因（强制留痕） */
    private String cancelReason;

    /** 提交时产品保质期天数快照 */
    private Integer shelfLifeDaysSnapshot;

    /** 喷码保质期与标准保质期差异（天） */
    private Integer expiryDiffDays;

    /** DRAFT/PENDING_RECEIVE/RECEIVED/ARCHIVED/CANCELLED */
    private String status;

    /** 流程版本快照 */
    private Long processDefId;

    @Version
    private Integer lockVersion;
}
