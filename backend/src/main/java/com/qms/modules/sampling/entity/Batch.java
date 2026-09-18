package com.qms.modules.sampling.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次台账（轻量效期管理）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_batch")
public class Batch extends BaseEntity {

    private Long id;

    private Long skuId;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    private String storageCondition;

    /** NORMAL/NEAR_EXPIRY/EXPIRED */
    private String status;

    private LocalDateTime lastCheckAt;
}
