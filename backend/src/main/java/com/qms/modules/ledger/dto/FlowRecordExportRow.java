package com.qms.modules.ledger.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 全流程记录报表导出行：一个商品的一个流程节点为一行。
 */
@Data
@ColumnWidth(18)
public class FlowRecordExportRow {

    @ExcelProperty("SKU")
    private String sku;

    @ExcelProperty("产品通用名")
    @ColumnWidth(24)
    private String commonName;

    @ExcelProperty("一级品类")
    private String categoryL1Name;

    @ExcelProperty("节点序号")
    private Integer nodeSort;

    @ExcelProperty("流程节点")
    private String nodeName;

    @ExcelProperty("节点状态")
    private String status;

    @ExcelProperty("完成日期")
    private String finishDate;

    @ExcelProperty("负责人")
    private String ownerName;

    @ExcelProperty("关联供应商")
    @ColumnWidth(26)
    private String supplierName;

    @ExcelProperty("结论")
    @ColumnWidth(30)
    private String conclusion;

    @ExcelProperty("备注")
    @ColumnWidth(30)
    private String remark;
}
