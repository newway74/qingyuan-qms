package com.qms.modules.standard.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 标准评审记录（提交评审/采购会签/老板批准，只增）。 */
@Data
@TableName("qc_std_review")
public class StdReview {

    @TableId
    private Long id;

    private Long templateId;

    /** SUBMIT/PROCUREMENT/BOSS */
    private String node;

    /** SUBMIT/PASS/REJECT/COMMENT */
    private String action;

    private String comment;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime operatedAt;

    private Long tenantId;
}
