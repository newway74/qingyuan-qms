package com.qms.modules.sampling.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 抽样单新建/草稿编辑。
 */
@Data
public class SamplingUpsertRequest {

    /** 编辑草稿时传 */
    private Long id;

    @NotBlank(message = "抽样来源不能为空")
    private String source;

    @NotNull(message = "SKU不能为空")
    private Long skuId;

    @NotBlank(message = "批号不能为空")
    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    private String storageCondition;

    @DecimalMin(value = "0", inclusive = false, message = "抽样数量必须大于0")
    private BigDecimal sampleQuantity;

    private String quantityUnit;

    private String samplingLocation;

    private LocalDateTime sampledAt;

    private String remark;
}
