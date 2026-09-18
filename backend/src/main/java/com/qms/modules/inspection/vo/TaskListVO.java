package com.qms.modules.inspection.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检验任务列表项（多视图共用）。
 */
@Data
public class TaskListVO {

    private Long id;
    private String taskNo;
    private Long sampleId;
    private String sampleNo;
    private Long skuId;
    private String skuCode;
    private String productName;
    private String spec;
    private String packageForm;
    private String batchNo;
    private Long templateId;
    private String templateName;
    private Integer templateVersion;
    private Long processDefId;
    private Long inspectorId;
    private String inspectorName;
    private Long reviewerId;
    private String reviewerName;
    private String status;
    private LocalDateTime slaDeadline;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Long recheckParentId;
    private Integer roundNo;
    private Integer resultCount;
    private Integer lockVersion;
    private LocalDateTime createdAt;
}
