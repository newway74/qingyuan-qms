package com.qms.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 供应商证照（到期日为阶段5证照预警依据）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_supplier_license")
public class SupplierLicense extends BaseEntity {

    private Long id;

    private Long supplierId;

    /** 营业执照/生产许可证/经营许可证 */
    private String licenseType;

    private String certNo;

    private LocalDate validFrom;

    private LocalDate validTo;

    private Long fileAttachmentId;

    /** 1有效 0失效 */
    private Integer status;
}
