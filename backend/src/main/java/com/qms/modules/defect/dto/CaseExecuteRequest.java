package com.qms.modules.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 处置执行登记：退货/下架/销毁/换货/整改完成的执行记录与凭证附件。
 */
@Data
public class CaseExecuteRequest {

    @NotBlank(message = "处置执行说明不能为空")
    private String comment;

    /** 凭证附件id，逗号分隔 */
    private String attachmentIds;
}
