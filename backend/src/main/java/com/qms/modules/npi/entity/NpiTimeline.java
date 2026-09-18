package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 新品项目阶段时间线（只增，不继承 BaseEntity）。 */
@Data
@TableName("qc_npi_timeline")
public class NpiTimeline {

    @TableId
    private Long id;

    private Long projectId;

    private String stage;

    private String action;

    private String title;

    private String comment;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdAt;

    private Long tenantId;
}
