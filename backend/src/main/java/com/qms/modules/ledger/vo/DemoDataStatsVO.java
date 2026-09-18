package com.qms.modules.ledger.vo;

import lombok.Data;

/**
 * 演示数据规模与一键清除结果。
 * 统计口径固定为 data_source='DEMO'；一键清除只影响 DEMO，账号/主数据/模板/USER 数据全部保留。
 */
@Data
public class DemoDataStatsVO {

    /** 演示商品数 */
    private long goodsCount;

    /** 演示供应商数 */
    private long supplierCount;

    /** 演示全流程实例数 */
    private long flowCount;

    /** 演示流程节点记录数 */
    private long flowNodeCount;

    /** 演示资料清单条数 */
    private long materialCount;

    /** 本次清除是否实际执行（统计查询时为 false） */
    private boolean cleared;
}
