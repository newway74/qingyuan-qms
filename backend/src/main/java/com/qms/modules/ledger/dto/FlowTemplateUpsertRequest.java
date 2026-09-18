package com.qms.modules.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程模板基本信息新增/编辑请求（节点编排走单独的批量保存接口）。
 */
@Data
public class FlowTemplateUpsertRequest {

    /** 为空表示新建 */
    private Long id;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称最长 100 个字符")
    private String templateName;

    @Size(max = 255)
    private String remark;

    /** 1 启用 0 停用；新建缺省启用 */
    private Integer status;
}
