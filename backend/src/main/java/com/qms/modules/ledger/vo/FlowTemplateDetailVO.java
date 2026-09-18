package com.qms.modules.ledger.vo;

import lombok.Data;

import java.util.List;

/**
 * 流程模板详情：模板基本信息 + 节点（含自定义字段配置）。
 */
@Data
public class FlowTemplateDetailVO {

    private Long id;
    private String templateCode;
    private String templateName;
    private Integer version;
    private Integer isPreset;
    private Integer status;
    private String remark;
    /** 使用该模板（任意版本）建档的商品数 */
    private Long boundGoodsCount;

    private List<NodeDefVO> nodes;

    @Data
    public static class NodeDefVO {
        private Long id;
        private String nodeCode;
        private String nodeName;
        private Integer nodeSort;
        private Integer linkSupplier;
        private String remark;
        private List<FieldDefVO> fields;
    }

    @Data
    public static class FieldDefVO {
        private Long id;
        private String fieldCode;
        private String fieldName;
        private String fieldType;
        private List<String> options;
        private Integer required;
        private Integer sort;
    }
}
