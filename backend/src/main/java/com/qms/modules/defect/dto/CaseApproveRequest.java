package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 主管审批：通过需密码二次认证（电子签名），驳回必须填写意见。
 */
@Data
public class CaseApproveRequest {

    /** PASS/REJECT */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    /** PASS 时必填（二次认证） */
    private String password;

    private String comment;
}
