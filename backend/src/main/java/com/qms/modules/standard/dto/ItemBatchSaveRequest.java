package com.qms.modules.standard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 检验项批量保存（仅 DRAFT 模板允许；服务端整组替换并逻辑删除旧行）。
 */
@Data
public class ItemBatchSaveRequest {

    @NotEmpty(message = "检验项至少保留一项")
    @Valid
    private List<StandardItemRequest> items;
}
