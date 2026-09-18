package com.qms.modules.inspection.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单项结果录入。
 */
@Data
public class ResultItemInput {

    /** qc_inspection_result 主键（首次由模板快照初始化后回传） */
    private Long resultId;

    /** 检验项快照id */
    private Long itemId;

    /** PASS/FAIL/NA */
    private String qualitativeValue;

    private BigDecimal quantitativeValue;

    private Long docAttachmentId;

    private String remark;
}
