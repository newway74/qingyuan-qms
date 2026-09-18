package com.qms.modules.npi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 新品送样评估单（同一项目可登记多家供应商，横向对比后定点）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_npi_eval")
public class NpiEval extends BaseEntity {

    private Long id;

    private String evalNo;

    private Long projectId;

    private Long supplierId;

    private Integer roundNo;

    private String sampleDesc;

    private LocalDateTime receivedAt;

    /** PENDING/QUALIFIED/UNQUALIFIED */
    private String qualityConclusion;

    private BigDecimal totalScore;

    private Integer rankNo;

    private Integer selectedFlag;

    private Long reportAttachmentId;

    private String remark;

    /** DRAFT/SUBMITTED */
    private String status;

    @Version
    private Integer lockVersion;
}
