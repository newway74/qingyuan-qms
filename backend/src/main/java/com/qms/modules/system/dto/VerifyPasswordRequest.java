package com.qms.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 敏感操作（电子签名）二次密码认证
 */
@Data
public class VerifyPasswordRequest {

    @NotBlank(message = "密码不能为空")
    private String password;
}
