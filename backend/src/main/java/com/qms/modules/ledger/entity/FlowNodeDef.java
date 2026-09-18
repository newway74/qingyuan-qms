package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模板节点定义（商品建档时整体快照到节点记录表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_flow_node_def")
public class FlowNodeDef extends BaseEntity {

    private Long id;

    private Long templateId;

    /** 节点编码（同一模板版本内唯一） */
    private String nodeCode;

    private String nodeName;

    /** 节点顺序，从小到大 */
    private Integer nodeSort;

    /** 该节点是否关联供应商（如供应商准入审核、实地验厂）1 是 0 否 */
    private Integer linkSupplier;

    private String remark;
}
