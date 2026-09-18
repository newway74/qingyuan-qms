package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 不合格单误判撤销（高级权限 + 强制留痕）。
 */
@Data
public class CaseCancelRequest {

    @NotBlank(message = "撤销原因不能为空")
    private String comment;
}
