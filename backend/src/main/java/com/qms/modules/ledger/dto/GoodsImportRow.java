package com.qms.modules.ledger.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 商品批量导入 Excel 行模型（按列下标绑定，表头文字仅用于展示，不参与解析）。
 * 全部读成字符串自行解析，兼容“文本日期/数值日期”两种常见填法，避免整行类型转换失败。
 *
 * 列顺序必须与模板下载里的表头严格一致，调整时两端同步。
 */
@Data
@ColumnWidth(20)
public class GoodsImportRow {

    @ExcelProperty(value = "SKU（必填，租户内唯一）", index = 0)
    private String sku;

    @ExcelProperty(value = "产品通用名（必填）", index = 1)
    private String commonName;

    @ExcelProperty(value = "规格", index = 2)
    private String spec;

    @ExcelProperty(value = "生产企业", index = 3)
    private String manufacturer;

    @ExcelProperty(value = "批准文号/备案号", index = 4)
    private String approvalNo;

    @ExcelProperty(value = "UPC(69码)", index = 5)
    private String upc;

    @ExcelProperty(value = "品牌", index = 6)
    private String brand;

    @ExcelProperty(value = "一级品类（填品类名称）", index = 7)
    private String categoryL1Name;

    @ExcelProperty(value = "二级品类（填品类名称，可空）", index = 8)
    private String categoryL2Name;

    @ExcelProperty(value = "过会时间(YYYY-MM-DD)", index = 9)
    private String meetingDate;

    @ExcelProperty(value = "合作结论（是/待定/否，留空按待定）", index = 10)
    private String cooperateResult;

    @ExcelProperty(value = "上市日期(YYYY-MM-DD)", index = 11)
    private String launchDate;

    @ExcelProperty(value = "准入供应商名称（不存在将自动新建）", index = 12)
    private String auditSupplierName;

    @ExcelProperty(value = "验厂供应商名称（不存在将自动新建）", index = 13)
    private String factorySupplierName;

    @ExcelProperty(value = "过会结论（填写即视为该节点已完成；含“不通过”视为不通过）", index = 14)
    private String meetingConclusion;

    @ExcelProperty(value = "准入审核完成日期(YYYY-MM-DD)", index = 15)
    private String auditFinishDate;

    @ExcelProperty(value = "供应商准入审核结论", index = 16)
    private String auditConclusion;

    @ExcelProperty(value = "实地验厂完成日期(YYYY-MM-DD)", index = 17)
    private String factoryFinishDate;

    @ExcelProperty(value = "实地验厂结论", index = 18)
    private String factoryConclusion;

    @ExcelProperty(value = "包装审核完成日期(YYYY-MM-DD)", index = 19)
    private String packageFinishDate;

    @ExcelProperty(value = "包装审核结论", index = 20)
    private String packageConclusion;

    @ExcelProperty(value = "上市结论", index = 21)
    private String listingConclusion;

    @ExcelProperty(value = "上市后监控完成日期(YYYY-MM-DD)", index = 22)
    private String postMarketFinishDate;

    @ExcelProperty(value = "上市后质量监控结论", index = 23)
    private String postMarketConclusion;

    @ExcelProperty(value = "当前节点（导入时自动计算，此列留空即可）", index = 24)
    private String currentNodeName;

    @ExcelProperty(value = "备注", index = 25)
    private String remark;
}
