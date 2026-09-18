package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSkuRequest {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "SKU编码不能为空")
    @Size(max = 50)
    private String skuCode;

    @Size(max = 150)
    private String spec;

    /** 袋装/罐装/盒装 */
    @Size(max = 30)
    private String packageForm;

    @PositiveOrZero(message = "标示净含量必须≥0")
    private BigDecimal netContent;

    @Size(max = 10)
    private String netContentUnit;

    @Size(max = 64)
    private String barcode;

    /** 1启用 0停用，空默认1 */
    private Integer status;
}
