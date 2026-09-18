package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 供应商整改单签发。
 */
@Data
public class RectificationCreateRequest {

    @NotNull(message = "不合格单id不能为空")
    private Long defectCaseId;

    @NotBlank(message = "问题描述不能为空")
    private String issueDesc;

    /** 要求完成日期 */
    private LocalDate planFinishDate;
}
