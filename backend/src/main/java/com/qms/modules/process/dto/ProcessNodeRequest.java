package com.qms.modules.process.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流程节点编排单行。
 */
@Data
public class ProcessNodeRequest {

    @NotBlank(message = "节点编码不能为空")
    private String nodeCode;

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 100)
    private String nodeName;

    /** SAMPLER/INSPECTOR/REVIEWER/QA_MANAGER */
    @NotBlank(message = "责任角色不能为空")
    private String responsibleRole;

    @PositiveOrZero(message = "SLA小时数必须≥0")
    private Integer slaHours = 0;

    /** NATURAL/WORKDAY，空默认 NATURAL */
    private String calendarType;

    /** 必填字段键 */
    private List<String> requiredFields;

    /** 通过/驳回规则 */
    private Map<String, Object> transitionRules;

    private Integer sort = 0;
}
