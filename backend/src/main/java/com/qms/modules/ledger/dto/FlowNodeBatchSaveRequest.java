package com.qms.modules.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 流程模板节点批量保存请求：整体替换某模板下的节点与字段配置。
 * 提交后模板版本号 +1；已建商品沿用旧快照，不强制追溯。
 */
@Data
public class FlowNodeBatchSaveRequest {

    @NotNull(message = "节点列表不能为空")
    @Valid
    private List<FlowNodeInput> nodes;

    @Data
    public static class FlowNodeInput {

        /** 节点编码：新建可传空由后端按序号生成；保存时按编码在商品快照外无业务含义，仅模板内唯一 */
        private String nodeCode;

        @NotBlank(message = "节点名称不能为空")
        @Size(max = 100)
        private String nodeName;

        @NotNull(message = "节点顺序不能为空")
        private Integer nodeSort;

        /** 是否关联供应商节点，默认 0 */
        private Integer linkSupplier;

        @Size(max = 255)
        private String remark;

        @Valid
        private List<FlowNodeFieldInput> fields;
    }

    @Data
    public static class FlowNodeFieldInput {

        private String fieldCode;

        @NotBlank(message = "字段名不能为空")
        @Size(max = 100)
        private String fieldName;

        @NotBlank(message = "字段类型不能为空")
        private String fieldType;

        /** SELECT 类型的下拉选项；其他类型忽略 */
        private List<String> options;

        private Integer required;

        private Integer sort;
    }
}
