package com.qms.modules.defect.vo;

import com.qms.modules.defect.entity.SupplierRectification;
import lombok.Data;

/**
 * 整改单视图：单据 + 供应商/不合格单/复检任务展示字段。
 */
@Data
public class RectificationVO {

    private SupplierRectification rect;

    private String supplierName;

    private String caseNo;

    private String recheckTaskNo;

    private String verifierName;
}
