package com.qms.modules.npi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EvalItemInput {

    @NotBlank
    private String dimensionCode;

    @NotBlank
    private String dimensionName;

    private BigDecimal score;

    private BigDecimal weight;

    private String note;
}
