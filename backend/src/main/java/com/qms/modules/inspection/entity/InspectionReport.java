package com.qms.modules.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检验结论与报告。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "qc_inspection_report", autoResultMap = true)
public class InspectionReport extends BaseEntity {

    private Long id;

    private Long taskId;

    private String reportNo;

    /** QUALIFIED/UNQUALIFIED/CONCESSION/PENDING_RECHECK */
    private String conclusion;

    private Integer aFailCount;

    private Integer bFailCount;

    private Integer cFailCount;

    @TableField(value = "defect_summary", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Map<String, Object>> defectSummary;

    private String inspectorSignHash;

    private LocalDateTime inspectorSignedAt;

    private String reviewerSignHash;

    private LocalDateTime reviewerSignedAt;

    private Long pdfAttachmentId;

    /** DRAFT/SIGNED/ISSUED */
    private String status;

    private LocalDateTime issuedAt;

    @Version
    private Integer lockVersion;
}
