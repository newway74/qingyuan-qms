package com.qms.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.Version;
import com.qms.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 商品品控台账主表：以商品（SKU）为中心的全流程台账一行一商品。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_ledger_goods")
public class LedgerGoods extends BaseEntity {

    private Long id;

    /** SKU 编码（租户内唯一，Excel 导入按此匹配新建/更新） */
    private String sku;

    /** 产品通用名 */
    private String commonName;

    /** 规格 */
    private String spec;

    /** 生产企业 */
    private String manufacturer;

    /** 批准文号/备案号 */
    private String approvalNo;

    /** UPC(69码) */
    private String upc;

    /** 品牌 */
    private String brand;

    /** 一级品类 id */
    private Long categoryL1Id;

    /** 二级品类 id */
    private Long categoryL2Id;

    /** 过会时间 */
    private LocalDate meetingDate;

    /** 最终合作结论 YES 是 / PENDING 待定 / NO 否 */
    private String cooperateResult;

    /** 上市日期 */
    private LocalDate launchDate;

    /** 当前流程节点编码（快照） */
    private String currentNodeCode;

    /** 当前流程节点名称（快照） */
    private String currentNodeName;

    /** 关联流程模板版本行 id */
    private Long templateId;

    /** 建档时模板版本 */
    private Integer templateVersion;

    /** 可选关联新品引入项目 id */
    private Long npiProjectId;

    /** 数据来源 USER 用户数据 / DEMO 演示数据（一键清除仅删 DEMO） */
    private String dataSource;

    /** 备注 */
    private String remark;

    @Version
    private Integer lockVersion;
}
