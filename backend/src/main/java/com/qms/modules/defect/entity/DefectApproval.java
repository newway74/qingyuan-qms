package com.qms.modules.defect.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 不合格处置审批时间线（只增）：每个节点动作一行，含前后快照与附件。
 */
@Data
@TableName(value = "qc_defect_approval", autoResultMap = true)
public class DefectApproval {

    private Long id;

    private Long caseId;

    /** REVIEW/APPROVAL/DISPOSE/RECTIFY/RECHECK/CLOSE */
    private String node;

    /** SUBMIT/PASS/REJECT/CLOSE/EXECUTE/CANCEL */
    private String action;

    private String comment;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> beforeSnapshot;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> afterSnapshot;

    /** 附件id逗号分隔 */
    private String attachmentIds;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime operatedAt;

    private Long tenantId;
}
