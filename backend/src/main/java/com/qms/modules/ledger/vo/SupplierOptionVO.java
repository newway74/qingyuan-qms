package com.qms.modules.ledger.vo;

import lombok.Data;

/**
 * 供应商简要选项（流程节点关联供应商用，仅返回台账页需要的字段，避免越权暴露证照等明细）。
 */
@Data
public class SupplierOptionVO {

    private Long id;
    private String supplierCode;
    private String supplierName;
    /** QUALIFIED 合格 / CONTROLLED 受控 / DISABLED 停用 */
    private String status;
}
