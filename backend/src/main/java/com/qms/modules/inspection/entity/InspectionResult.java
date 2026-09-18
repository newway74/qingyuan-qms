package com.qms.modules.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 逐项检验结果（含模板检验项快照与判定规则快照）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "qc_inspection_result", autoResultMap = true)
public class InspectionResult extends BaseEntity {

    private Long id;

    private Long taskId;

    /** 检验项版本快照id */
    private Long itemId;

    private String groupCode;

    private String itemName;

    /** QUALITATIVE/QUANTITATIVE/DOCUMENT */
    private String resultType;

    /** PASS/FAIL/NA */
    private String qualitativeValue;

    private BigDecimal quantitativeValue;

    private String unit;

    private Long docAttachmentId;

    /** 系统自动判定 PASS/FAIL/NONE */
    private String autoJudgement;

    /** 复核后最终判定 */
    private String finalJudgement;

    /** 复核改判说明（仅B/C类可改判） */
    private String reviewNote;

    @TableField(value = "judge_snapshot", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, Object> judgeSnapshot;

    private String remark;

    private Long enteredBy;

    private java.time.LocalDateTime enteredAt;
}
