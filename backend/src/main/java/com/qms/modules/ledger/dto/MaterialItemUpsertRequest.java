package com.qms.modules.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资料项定义维护请求（仅管理员：新建/编辑资料项，如产前样品、型式检验报告等）。
 */
@Data
public class MaterialItemUpsertRequest {

    /** 编辑时必填；新建为空由后端生成雪花编码 */
    private Long id;

    @NotBlank(message = "资料项名称不能为空")
    @Size(max = 100)
    private String itemName;

    /** 排序，可空（新建时取当前最大值 +10） */
    private Integer sort;

    /** 1 启用 0 停⽤，新建默认启用 */
    private Integer status;
}
