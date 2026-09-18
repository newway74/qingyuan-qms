package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductUpsertRequest {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "SPU编码不能为空")
    @Size(max = 50)
    private String spuCode;

    @NotBlank(message = "产品名称不能为空")
    @Size(max = 200)
    private String productName;

    @NotNull(message = "品类不能为空")
    private Long categoryId;

    @Size(max = 100)
    private String brand;

    private Long supplierId;

    @Size(max = 100)
    private String executionStandard;

    @Size(max = 200)
    private String storageCondition;

    @PositiveOrZero(message = "保质期天数必须≥0")
    private Integer shelfLifeDays;

    /** 1需要第三方外检 0不需要，空默认0 */
    private Integer extInspectionRequired;

    /** NORMAL/CONTROLLED/FROZEN/DISABLED，空默认 NORMAL */
    private String qualityStatus;
}
