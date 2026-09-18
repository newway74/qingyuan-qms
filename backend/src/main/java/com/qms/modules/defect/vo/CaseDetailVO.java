package com.qms.modules.defect.vo;

import com.qms.modules.defect.entity.DefectApproval;
import com.qms.modules.defect.entity.DefectCase;
import com.qms.modules.defect.entity.DefectItem;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 不合格单详情：单据 + 缺陷明细 + 审批时间线 + 整改单 + 当前状态可执行动作。
 */
@Data
public class CaseDetailVO {

    private DefectCase caseObj;

    private String reportNo;

    private String taskNo;

    private String sampleNo;

    private String productName;

    private String spec;

    private String batchNo;

    private String supplierName;

    private Long supplierId;

    private String conclusion;

    private Integer aFailCount;

    private Integer bFailCount;

    private Integer cFailCount;

    private String issuedAt;

    private List<DefectItem> items;

    private List<DefectApproval> timeline;

    private RectificationVO rectification;

    /** 状态机允许当前用户执行的动作 */
    private Set<String> allowedActions;
}
