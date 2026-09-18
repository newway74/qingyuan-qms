package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SupplierLicenseRequest {

    /** 编辑时必填 */
    private Long id;

    @NotBlank(message = "证照类型不能为空")
    @Size(max = 50)
    private String licenseType;

    @Size(max = 100)
    private String certNo;

    private LocalDate validFrom;

    /** 到期日（证照预警依据） */
    private LocalDate validTo;

    private Long fileAttachmentId;

    /** 1有效 0失效，空则默认1 */
    private Integer status;
}
