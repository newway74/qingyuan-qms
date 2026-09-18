package com.qms.modules.process.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 节点编排批量保存（仅 DRAFT；九节点必须齐全且不重复）。
 */
@Data
public class NodeBatchSaveRequest {

    @NotEmpty(message = "至少配置一个节点")
    @Valid
    private List<ProcessNodeRequest> nodes;
}
