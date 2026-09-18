package com.qms.modules.ledger.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 资料缺口清单报表导出行：一个商品的一项缺失/待确认资料为一行。
 */
@Data
@ColumnWidth(18)
public class MaterialGapExportRow {

    @ExcelProperty("SKU")
    private String sku;

    @ExcelProperty("产品通用名")
    @ColumnWidth(24)
    private String commonName;

    @ExcelProperty("品牌")
    private String brand;

    @ExcelProperty("一级品类")
    private String categoryL1Name;

    @ExcelProperty("当前流程节点")
    private String currentNodeName;

    @ExcelProperty("缺失资料项")
    @ColumnWidth(24)
    private String itemName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;
}
