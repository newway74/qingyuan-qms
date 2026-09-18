package com.qms.modules.process.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 流程节点（九节点固定模型，顺序可编排）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "qc_process_node", autoResultMap = true)
public class ProcessNode extends BaseEntity {

    private Long id;

    private Long processDefId;

    /** SAMPLING/RECEIVE/ASSIGN/INSPECT/REVIEW/JUDGE/DEFECT/RECHECK/ARCHIVE */
    private String nodeCode;

    private String nodeName;

    /** SAMPLER/INSPECTOR/REVIEWER/QA_MANAGER */
    private String responsibleRole;

    /** SLA 自然小时 */
    private Integer slaHours;

    /** NATURAL 自然时间 / WORKDAY 工作日历（预留） */
    private String calendarType;

    /** 必填字段键集合 */
    @TableField(value = "required_fields", typeHandler = JacksonTypeHandler.class)
    private List<String> requiredFields;

    /** 通过/驳回规则 JSON */
    @TableField(value = "transition_rules", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> transitionRules;

    private Integer sort;
}
