package com.qms.modules.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 复核改判项：仅允许 B/C 类，必须填改判理由。
 */
@Data
public class ReviewAdjustment {

    private Long resultId;

    @NotBlank(message = "改判结论不能为空")
    private String finalJudgement;

    @NotBlank(message = "改判理由不能为空")
    private String reviewNote;
}
