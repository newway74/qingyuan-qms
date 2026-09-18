package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 让步接收审批：仅 B/C 类（A 类后端硬拦截），密码二次认证。
 */
@Data
public class CaseConcessionRequest {

    @NotBlank(message = "二次密码不能为空")
    private String password;

    @NotBlank(message = "让步接收理由不能为空")
    private String comment;
}
