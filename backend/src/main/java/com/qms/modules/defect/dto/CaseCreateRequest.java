package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动对已双签/已签发报告建立不合格处置单。
 */
@Data
public class CaseCreateRequest {

    @NotNull(message = "报告id不能为空")
    private Long reportId;

    /** 建档说明（留痕） */
    private String comment;
}
