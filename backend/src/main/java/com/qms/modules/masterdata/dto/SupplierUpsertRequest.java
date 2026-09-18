package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierUpsertRequest {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 50, message = "供应商编码最长50")
    private String supplierCode;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 150, message = "供应商名称最长150")
    private String supplierName;

    @Size(max = 64)
    private String contact;

    @Size(max = 32)
    private String phone;

    @Size(max = 255)
    private String address;

    /** QUALIFIED/CONTROLLED/DISABLED，空则默认 QUALIFIED */
    private String status;
}
