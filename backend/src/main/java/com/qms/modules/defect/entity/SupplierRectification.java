package com.qms.modules.defect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseVersionEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商整改单（ZG）：签发→回复→验证触发复检→通过/失败。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_supplier_rectification")
public class SupplierRectification extends BaseVersionEntity {

    private Long id;

    private String rectifyNo;

    private Long defectCaseId;

    private Long supplierId;

    private String issueDesc;

    private String correctiveAction;

    private LocalDate planFinishDate;

    private LocalDate actualFinishDate;

    /** ISSUED/REPLIED/VERIFYING/PASSED/FAILED */
    private String status;

    private Long recheckTaskId;

    private Long verifierId;

    private LocalDateTime verifiedAt;
}
