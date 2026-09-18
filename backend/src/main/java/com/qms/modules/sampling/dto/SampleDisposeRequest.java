package com.qms.modules.sampling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 样品处置（销毁/退还，全程留痕）。
 */
@Data
public class SampleDisposeRequest {

    @NotNull(message = "样品id不能为空")
    private Long id;

    /** DESTROY 销毁 / RETURN 退还 */
    @NotBlank(message = "处置方式不能为空")
    private String disposeType;

    @NotBlank(message = "处置说明不能为空")
    private String disposeRemark;
}
