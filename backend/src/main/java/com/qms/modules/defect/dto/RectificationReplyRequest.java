package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 供应商整改回复：纠正措施 + 计划完成日（+ 实际完成日）。
 * 首次回复（ISSUED）计划完成日期必填，由服务层校验；
 * 验证失败后重新回复（FAILED）允许不传，沿用原计划完成日。
 */
@Data
public class RectificationReplyRequest {

    @NotBlank(message = "纠正措施不能为空")
    private String correctiveAction;

    private LocalDate planFinishDate;

    private LocalDate actualFinishDate;
}
