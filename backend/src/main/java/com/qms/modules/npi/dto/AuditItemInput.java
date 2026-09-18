package com.qms.modules.npi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuditItemInput {

    @NotBlank
    private String category;

    @NotBlank
    private String itemName;

    private String requirement;

    private BigDecimal maxScore = new BigDecimal("100");

    private BigDecimal score;

    /** COMPLIANT/MINOR/MAJOR/NA */
    private String result;

    private String note;

    private Integer sort;
}
