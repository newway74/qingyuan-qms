package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductLicenseRequest {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "资质类型不能为空")
    @Size(max = 50)
    private String licenseType;

    @Size(max = 100)
    private String certNo;

    private LocalDate validTo;

    private Long fileAttachmentId;
}
