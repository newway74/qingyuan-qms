package com.qms.modules.ledger.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品品控工作台（统计主页）数据。
 */
@Data
public class LedgerDashboardVO {

    /** 顶部统计卡片 */
    private Cards cards;

    /** 过会状态分布（已过会/待定/不合作） */
    private List<NameValue> cooperateResultDist;

    /** 当前节点分布（current_node_code 为空归入“已完成全流程”） */
    private List<NameValue> currentNodeDist;

    /** 一级品类分布 */
    private List<NameValue> categoryDist;

    /** 顶部四张统计卡片 */
    @Data
    public static class Cards {
        /** 商品总数 */
        private long goodsTotal;
        /** 供应商总数 */
        private long supplierTotal;
        /** 全流程记录数（已产生办理动作的节点记录：进行中/已完成/不通过，不含未开始） */
        private long flowRecordTotal;
        /** 资料重点缺口数（全部商品“缺失/待确认”的资料项之和） */
        private long gapItemTotal;
    }

    /** 图表通用 名称-数值 对 */
    @Data
    public static class NameValue {
        private String name;
        private long value;

        public NameValue() {
        }

        public NameValue(String name, long value) {
            this.name = name;
            this.value = value;
        }
    }
}
