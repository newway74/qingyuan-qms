package com.qms.modules.npi.vo;

import com.qms.modules.npi.entity.FactoryAudit;
import com.qms.modules.npi.entity.FactoryAuditItem;
import lombok.Data;

import java.util.List;

@Data
public class AuditDetailVO {

    private FactoryAudit audit;

    private String supplierName;

    private String projectName;

    private List<FactoryAuditItem> items;
}
