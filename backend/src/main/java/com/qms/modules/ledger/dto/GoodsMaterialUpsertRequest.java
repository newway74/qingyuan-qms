package com.qms.modules.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品资料清单单项状态更新请求（齐套 / 缺失 / 待确认 + 备注）。
 */
@Data
public class GoodsMaterialUpsertRequest {

    /** READY 齐套 / MISSING 缺失 / PENDING 待确认 */
    @NotBlank(message = "资料状态不能为空")
    @Size(max = 10)
    private String status;

    @Size(max = 500)
    private String remark;
}
