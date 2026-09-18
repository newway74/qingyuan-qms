package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 商品全流程节点记录：建档时由模板节点快照生成，后续按实际办理结果更新。
 * 自定义字段值以 JSON 存 field_values：{fieldCode: 值}。
 * 附件走统一附件服务（bizType=LEDGER_NODE, bizId=本记录 id）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_ledger_flow_node")
public class LedgerFlowNode extends BaseEntity {

    private Long id;

    private Long flowId;

    private Long goodsId;

    private String nodeCode;

    private String nodeName;

    private Integer nodeSort;

    /** 是否关联供应商节点 1 是 0 否 */
    private Integer linkSupplier;

    /** 关联供应商 id（准入/验厂节点） */
    private Long supplierId;

    /** NOT_STARTED 未开始 / IN_PROGRESS 进行中 / DONE 已完成 / REJECTED 不通过 */
    private String status;

    private LocalDate finishDate;

    /** 负责人（姓名） */
    private String ownerName;

    private String conclusion;

    private String remark;

    /** 自定义字段值 JSON */
    private String fieldValues;
}
