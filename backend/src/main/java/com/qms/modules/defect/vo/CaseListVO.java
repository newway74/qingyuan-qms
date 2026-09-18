package com.qms.modules.defect.vo;

import com.qms.modules.defect.entity.DefectCase;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不合格单列表行：单据 + 来源报告/样品/供应商冗余展示字段。
 */
@Data
public class CaseListVO {

    private DefectCase caseObj;

    private String reportNo;

    private String taskNo;

    private String sampleNo;

    private String productName;

    private String spec;

    private String batchNo;

    private String supplierName;

    private String conclusion;

    private Integer aFailCount;

    private Integer bFailCount;

    private Integer cFailCount;

    private Integer itemCount;

    private LocalDateTime issuedAt;
}
