package com.qms.modules.npi.vo;

import com.qms.modules.npi.entity.NpiProject;
import lombok.Data;

@Data
public class ProjectListVO {

    private NpiProject project;

    private String categoryName;

    private String chosenSupplierName;

    private String productName;

    private String initiatorName;
}
