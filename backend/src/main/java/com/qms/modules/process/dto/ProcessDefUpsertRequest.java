package com.qms.modules.process.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProcessDefUpsertRequest {

    /** 编辑时必填（草稿行id） */
    private Long id;

    /** 新建草稿时必填，跨版本业务编码（一经使用不可复用） */
    @Size(max = 50)
    private String processCode;

    @NotBlank(message = "流程名称不能为空")
    @Size(max = 200)
    private String processName;

    @Size(max = 500)
    private String remark;
}
