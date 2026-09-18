package com.qms.modules.inspection.vo;

import com.qms.modules.inspection.entity.InspectionReport;
import lombok.Data;

/**
 * 质检报告视图。
 */
@Data
public class ReportVO {

    private InspectionReport report;

    private String taskNo;
    private String sampleNo;
    private String skuCode;
    private String productName;
    private String spec;
    private String batchNo;
    private String inspectorName;
    private String reviewerName;
    private String templateName;
    private Integer templateVersion;
}
