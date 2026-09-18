package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点自定义字段配置：字段名 + 类型 + 下拉选项 + 必填 + 排序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_flow_node_field_def")
public class FlowNodeFieldDef extends BaseEntity {

    private Long id;

    private Long nodeDefId;

    private String fieldCode;

    private String fieldName;

    /** TEXT 文本 / DATE 日期 / SELECT 下拉 / FILE 附件 / CONCLUSION 结论 / TEXTAREA 多行文本 */
    private String fieldType;

    /** SELECT 下拉选项 JSON 数组，如 ["选项A","选项B"] */
    private String optionsJson;

    /** 是否必填 1 是 0 否 */
    private Integer required;

    private Integer sort;
}
