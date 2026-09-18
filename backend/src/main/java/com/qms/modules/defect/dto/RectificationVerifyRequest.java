package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 整改验证：PASS 触发复检子任务，FAIL 退回处置中；密码二次认证。
 */
@Data
public class RectificationVerifyRequest {

    /** PASS/FAIL */
    @NotBlank(message = "验证结论不能为空")
    private String result;

    @NotBlank(message = "二次密码不能为空")
    private String password;

    private String comment;
}
