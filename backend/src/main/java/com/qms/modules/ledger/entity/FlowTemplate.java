package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品全流程模板（名称 + 版本；节点见 qc_flow_node_def）。
 * 模板节点被修改后版本号 +1，已建商品保留建档时的节点快照，不强制追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_flow_template")
public class FlowTemplate extends BaseEntity {

    private Long id;

    private String templateCode;

    private String templateName;

    private Integer version;

    /** 1 系统预置 0 自建 */
    private Integer isPreset;

    /** 1 启用 0 停用 */
    private Integer status;

    private String remark;

    @Version
    private Integer lockVersion;
}
