package com.qms.modules.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验任务。template_id/process_def_id 为版本快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspection_task")
public class InspectionTask extends BaseEntity {

    private Long id;

    private String taskNo;

    private Long sampleId;

    private Long templateId;

    private Long processDefId;

    private Long inspectorId;

    private Long reviewerId;

    /** PENDING_ASSIGN/PENDING_INSPECT/INSPECTING/PENDING_REVIEW/JUDGED/RECHECKING/CLOSED */
    private String status;

    private LocalDateTime slaDeadline;

    private LocalDateTime assignedAt;

    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private Long recheckParentId;

    private String recheckReason;

    /** 复核驳回原因（留痕） */
    private String reviewRejectReason;

    private Integer roundNo;

    @Version
    private Integer lockVersion;
}
