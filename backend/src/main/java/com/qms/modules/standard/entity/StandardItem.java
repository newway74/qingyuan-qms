package com.qms.modules.standard.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 检验项（隶属于某个模板版本行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "qc_standard_item", autoResultMap = true)
public class StandardItem extends BaseEntity {

    private Long id;

    private Long templateId;

    /** SENSORY 感官 / PACKAGE_LABEL 包装标签 / NET_CONTENT 净含量 / PHYSICO 理化 / MICRO 微生物 / CERT_DOC 资质资料 */
    private String groupCode;

    private String itemName;

    private String inspectMethod;

    /** QUALITATIVE 定性 / QUANTITATIVE 定量 / DOCUMENT 资料上传 */
    private String resultType;

    /** A/B/C 缺陷等级 */
    private String defectLevel;

    /** 一票否决 */
    private Integer vetoFlag;

    /** 必检 */
    private Integer requiredFlag;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    /** 标示值（净含量） */
    private BigDecimal nominalValue;

    private String unit;

    /** 短缺量规则编码，如 JJF1070 */
    private String toleranceRule;

    /** 扩展判定规则：{operator, value, ...} */
    @TableField(value = "judge_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> judgeConfig;

    private Integer sort;
}
