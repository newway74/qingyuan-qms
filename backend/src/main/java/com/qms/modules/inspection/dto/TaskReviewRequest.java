package com.qms.modules.inspection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 复核判定请求。
 * action=PASS：密码二次认证 + 电子签名；可携带 B/C 改判与最终结论（QUALIFIED/CONCESSION）。
 * action=REJECT：驳回必须填写原因，退回检验员。
 */
@Data
public class TaskReviewRequest {

    @NotBlank(message = "复核动作不能为空")
    private String action;

    private String password;

    private String rejectReason;

    /** 复核确认结论：不传则采用系统建议；仅可 QUALIFIED / CONCESSION */
    private String conclusion;

    @Valid
    private List<ReviewAdjustment> adjustments;
}
