package com.qms.modules.sampling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 抽样单撤销（强制原因留痕）。
 */
@Data
public class SamplingCancelRequest {

    @NotNull(message = "id不能为空")
    private Long id;

    @NotBlank(message = "撤销原因不能为空")
    @Size(max = 500, message = "撤销原因最长500字")
    private String reason;
}
