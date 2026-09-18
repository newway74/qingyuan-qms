package com.qms.modules.sampling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 留样登记：留样位置与留样到期日（缺失禁转留样）。
 */
@Data
public class SampleRetainRequest {

    @NotNull(message = "样品id不能为空")
    private Long id;

    @NotBlank(message = "留样位置不能为空")
    private String retainLocation;

    @NotNull(message = "留样到期日不能为空")
    private LocalDate retainUntil;
}
