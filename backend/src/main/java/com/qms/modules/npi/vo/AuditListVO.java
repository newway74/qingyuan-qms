package com.qms.modules.npi.vo;

import com.qms.modules.npi.entity.FactoryAudit;
import lombok.Data;

@Data
public class AuditListVO {

    private FactoryAudit audit;

    private String supplierName;

    private String projectName;
}
