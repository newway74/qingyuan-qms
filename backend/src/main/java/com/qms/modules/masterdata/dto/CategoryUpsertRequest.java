package com.qms.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryUpsertRequest {

    /** 编辑时必填，新增不传 */
    private Long id;

    /** 顶级父节点传 0 */
    @NotNull(message = "父节点不能为空")
    private Long parentId;

    @NotBlank(message = "品类编码不能为空")
    @Size(max = 50, message = "品类编码最长50字符")
    private String code;

    @NotBlank(message = "品类名称不能为空")
    @Size(max = 100, message = "品类名称最长100字符")
    private String name;

    private Integer sort;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
