package com.qms.modules.standard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模板草稿新建/编辑。仅 DRAFT 行允许编辑。
 */
@Data
public class TemplateUpsertRequest {

    /** 编辑时必填（草稿行id） */
    private Long id;

    /** 新建草稿时必填，跨版本业务编码（一经使用不可复用） */
    @Size(max = 50)
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200)
    private String templateName;

    @NotNull(message = "适用品类不能为空")
    private Long categoryId;

    /** 袋装/罐装/盒装，空=全部形态 */
    @Size(max = 30)
    private String packageForm;

    /** 新品引入档位 HIGH/MID/LOW，空=通用标准 */
    @Size(max = 8)
    private String grade;

    private Long npiProjectId;

    @Size(max = 500)
    private String regulationBasis;

    @Size(max = 1000)
    private String marketBenchmark;

    @PositiveOrZero(message = "A类允许不合格数必须≥0")
    private Integer maxAFail = 0;

    @PositiveOrZero(message = "B类允许不合格数必须≥0")
    private Integer maxBFail = 0;

    @PositiveOrZero(message = "C类允许不合格数必须≥0")
    private Integer maxCFail = 0;

    /** 是否允许让步接收 1/0 */
    private Integer concessionAllowed = 0;

    @Size(max = 500)
    private String remark;
}
