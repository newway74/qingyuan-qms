package com.qms.modules.standard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 检验项批量保存中的单行。
 */
@Data
public class StandardItemRequest {

    @NotBlank(message = "检验分组不能为空")
    private String groupCode;

    @NotBlank(message = "检验项名称不能为空")
    @Size(max = 200)
    private String itemName;

    @Size(max = 200)
    private String inspectMethod;

    @NotBlank(message = "结果类型不能为空")
    private String resultType;

    @NotBlank(message = "缺陷等级不能为空")
    private String defectLevel;

    private Integer vetoFlag = 0;

    private Integer requiredFlag = 1;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    private BigDecimal nominalValue;

    @Size(max = 20)
    private String unit;

    @Size(max = 50)
    private String toleranceRule;

    /** 扩展判定规则，原样持久化到 judge_config JSON */
    private Map<String, Object> judgeConfig;

    private Integer sort = 0;
}
