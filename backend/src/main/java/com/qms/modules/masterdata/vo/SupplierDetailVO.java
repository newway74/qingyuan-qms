package com.qms.modules.masterdata.vo;

import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.entity.SupplierLicense;
import com.qms.modules.masterdata.entity.SupplierQuality;
import lombok.Data;

import java.util.List;

@Data
public class SupplierDetailVO {

    private Supplier supplier;

    private List<SupplierLicense> licenses;

    /** 质量评级历史（按周期倒序，阶段4开始写入） */
    private List<SupplierQuality> qualityRatings;
}
