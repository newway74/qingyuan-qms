package com.qms.modules.standard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新品项目内，依据品类现行标准生成某档位草稿。 */
@Data
public class GradeTemplateGenerateRequest {

    @NotNull(message = "新品项目不能为空")
    private Long projectId;

    @NotBlank(message = "档位不能为空")
    private String grade;

    @Size(max = 200)
    private String templateName;

    @Size(max = 500)
    private String regulationBasis;

    @Size(max = 1000)
    private String marketBenchmark;
}
