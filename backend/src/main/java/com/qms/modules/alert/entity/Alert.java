package com.qms.modules.alert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质量预警（SLA/留样/证照/批次）。dedup_key 唯一键保证重复扫描不重复落账。
 */
@Data
@TableName("qc_alert")
public class Alert implements Serializable {

    private Long id;

    /** SLA_WARNING/SLA_OVERDUE/RETAIN_EXPIRE/LICENSE_EXPIRE/NEAR_EXPIRY/BATCH_EXPIRED */
    private String alertType;

    private String bizType;

    private Long bizId;

    private String targetRole;

    private Long targetUserId;

    private String message;

    /** 1提示 2警告 3严重 */
    private Integer level;

    /** 0未处理 1已处理 */
    private Integer status;

    private String dedupKey;

    private LocalDateTime triggeredAt;

    private LocalDateTime handledAt;

    private Long handlerId;

    private Long tenantId;
}
