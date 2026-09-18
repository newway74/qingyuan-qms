package com.qms.modules.inspection.vo;

import lombok.Data;

import java.util.List;

/**
 * PDF 质检报告渲染数据。
 */
@Data
public class ReportPdfData {

    private String reportNo;
    private String taskNo;
    private String sampleNo;
    private String productName;
    private String skuCode;
    private String spec;
    private String batchNo;
    private String productionDate;
    private String expiryDate;
    private String templateName;
    private Integer templateVersion;
    private String conclusion;
    private Integer aFailCount;
    private Integer bFailCount;
    private Integer cFailCount;
    private String inspectorName;
    private String reviewerName;
    private String inspectorSignedAt;
    private String reviewerSignedAt;
    private String issuedAt;
    private String reportStatus;
    private String inspectorHash;
    private String reviewerHash;
    private String roundNo;
    private List<ResultRow> rows;

    @Data
    public static class ResultRow {
        private String groupCode;
        private String itemName;
        private String standard;
        private String measured;
        private String finalJudgement;
        private String reviewNote;
    }
}
