package com.qms.modules.ledger.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 商品详情：基础信息 + 流程线（节点记录与自定义字段定义合并）+ 资料清单。
 */
@Data
public class GoodsDetailVO {

    private Long id;
    private String sku;
    private String commonName;
    private String spec;
    private String manufacturer;
    private String approvalNo;
    private String upc;
    private String brand;
    private Long categoryL1Id;
    private Long categoryL2Id;
    private String categoryL1Name;
    private String categoryL2Name;
    private LocalDate meetingDate;
    private String cooperateResult;
    private LocalDate launchDate;
    /** 当前流程节点编码/名称（快照，详情页步骤条定位） */
    private String currentNodeCode;
    private String currentNodeName;
    private Long templateId;
    private Integer templateVersion;
    private String templateName;
    private Long npiProjectId;
    private String dataSource;
    private String remark;

    /** 模板最新版本（用于提示“模板已更新，可沿用本商品建档时版本”） */
    private Integer latestTemplateVersion;
    /** 商品建档版本是否落后于模板最新版本（仅提示，不强制追溯） */
    private Boolean templateOutdated;

    private List<FlowNodeVO> nodes;
    private List<MaterialVO> materials;
    /** 资料重点缺口数（缺失 + 待确认） */
    private Long gapCount;

    @Data
    public static class FlowNodeVO {
        private Long id;
        private String nodeCode;
        private String nodeName;
        private Integer nodeSort;
        private Integer linkSupplier;
        private Long supplierId;
        private String supplierName;
        /** NOT_STARTED / IN_PROGRESS / DONE / REJECTED */
        private String status;
        private LocalDate finishDate;
        private String ownerName;
        private String conclusion;
        private String remark;
        /** 自定义字段当前值，key=fieldCode */
        private Map<String, Object> fieldValues;
        /** 该节点自定义字段定义 */
        private List<FlowTemplateDetailVO.FieldDefVO> fieldDefs;
    }

    @Data
    public static class MaterialVO {
        private Long id;
        private Long itemId;
        private String itemCode;
        private String itemName;
        /** READY / MISSING / PENDING */
        private String status;
        private String remark;
        /** 资料项定义是否仍启用（停用/删除项的历史快照不计入缺口） */
        private Boolean enabled;
    }
}
