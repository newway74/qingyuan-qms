package com.qms.modules.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 检验员提交复核（二次密码认证 + 电子签名）。
 */
@Data
public class TaskSubmitRequest {

    @NotBlank(message = "请输入登录密码完成电子签名")
    private String password;
}
