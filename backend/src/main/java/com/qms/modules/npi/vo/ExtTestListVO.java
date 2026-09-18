package com.qms.modules.npi.vo;

import com.qms.modules.npi.entity.ExternalTest;
import lombok.Data;

@Data
public class ExtTestListVO {

    private ExternalTest test;

    private String supplierName;

    private String projectName;
}
