package com.qms.modules.standard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 标准评审动作：node=PROCUREMENT 采购会签 / BOSS 老板批准；pass=false 驳回回草稿。 */
@Data
public class StdReviewRequest {

    @NotBlank(message = "评审节点不能为空")
    private String node;

    private Boolean pass = Boolean.TRUE;

    private String comment;
}
