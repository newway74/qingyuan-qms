package com.qms.modules.ledger.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 商品台账报表导出行（含流程总览字段）。列顺序即 Excel 列顺序。
 */
@Data
@ColumnWidth(18)
public class GoodsLedgerExportRow {

    @ExcelProperty("SKU")
    private String sku;

    @ExcelProperty("产品通用名")
    @ColumnWidth(24)
    private String commonName;

    @ExcelProperty("规格")
    private String spec;

    @ExcelProperty("生产企业")
    @ColumnWidth(26)
    private String manufacturer;

    @ExcelProperty("批准文号/备案号")
    private String approvalNo;

    @ExcelProperty("UPC(69码)")
    private String upc;

    @ExcelProperty("品牌")
    private String brand;

    @ExcelProperty("一级品类")
    private String categoryL1Name;

    @ExcelProperty("二级品类")
    private String categoryL2Name;

    @ExcelProperty("过会时间")
    private String meetingDate;

    @ExcelProperty("最终合作结论")
    private String cooperateResult;

    @ExcelProperty("上市日期")
    private String launchDate;

    @ExcelProperty("当前流程节点")
    private String currentNodeName;

    @ExcelProperty("资料缺口数")
    private Integer gapCount;

    @ExcelProperty("数据来源")
    private String dataSource;

    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;
}
