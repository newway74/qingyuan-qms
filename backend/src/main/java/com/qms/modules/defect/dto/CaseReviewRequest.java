package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 质量评审：缺陷定级随单（按检验结果自动汇总），评审确定处置方式与根本原因。
 */
@Data
public class CaseReviewRequest {

    /** RETURN/OFF_SHELF/DESTROY/EXCHANGE/RECTIFY/CONCESSION */
    @NotBlank(message = "处置方式不能为空")
    private String disposition;

    @NotBlank(message = "根本原因分析不能为空")
    private String rootCause;

    /** 评审意见（留痕） */
    private String comment;
}
