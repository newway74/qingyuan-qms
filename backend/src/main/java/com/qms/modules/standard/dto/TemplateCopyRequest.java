package com.qms.modules.standard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 复制模板为新草稿（新业务编码，版本从1开始）。
 */
@Data
public class TemplateCopyRequest {

    @NotBlank(message = "新模板编码不能为空")
    @Size(max = 50)
    private String templateCode;

    @Size(max = 200)
    private String templateName;
}
