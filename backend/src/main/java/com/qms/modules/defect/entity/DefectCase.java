package com.qms.modules.defect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseVersionEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 不合格处置单（BH）。报告签发不合格时事务内自动生成，支持手动建档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_defect_case")
public class DefectCase extends BaseVersionEntity {

    private Long id;

    private String caseNo;

    private Long reportId;

    private Long taskId;

    private Long sampleId;

    /** A/B/C 最高严重等级 */
    private String maxSeverity;

    /** RETURN/OFF_SHELF/DESTROY/EXCHANGE/RECTIFY/CONCESSION */
    private String disposition;

    private String rootCause;

    /** PENDING_REVIEW/PENDING_APPROVAL/PROCESSING/PENDING_RECHECK/CLOSED/CANCELLED */
    private String status;

    private Long ownerId;

    private LocalDateTime closedAt;
}
