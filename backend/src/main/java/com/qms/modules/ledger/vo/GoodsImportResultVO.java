package com.qms.modules.ledger.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 批量导入结果：成功/失败/跳过计数 + 逐行明细（失败原因可直接回溯到 Excel 行号）。
 */
@Data
public class GoodsImportResultVO {

    /** 总行数（不含表头与示例行/空行） */
    private int total;

    /** 新建条数 */
    private int createdCount;

    /** 更新条数 */
    private int updatedCount;

    /** 跳过条数（选择“跳过已有”策略时命中已存在 SKU） */
    private int skippedCount;

    /** 失败条数 */
    private int failedCount;

    /** 逐行结果 */
    private List<RowResult> rows = new ArrayList<>();

    public void addRow(RowResult row) {
        rows.add(row);
        total++;
        if (row.isSuccess()) {
            switch (row.getAction()) {
                case "CREATED" -> createdCount++;
                case "UPDATED" -> updatedCount++;
                case "SKIPPED" -> skippedCount++;
                default -> {
                    // 成功行仅上述三种动作
                }
            }
        } else {
            failedCount++;
        }
    }

    @Data
    public static class RowResult {

        /** Excel 物理行号（表头为第 1 行） */
        private int rowNum;

        private String sku;

        /** CREATED 新建 / UPDATED 更新 / SKIPPED 跳过 / FAILED 失败 */
        private String action;

        private boolean success;

        /** 失败原因或动作说明 */
        private String reason;

        public static RowResult ok(int rowNum, String sku, String action, String reason) {
            RowResult r = new RowResult();
            r.rowNum = rowNum;
            r.sku = sku;
            r.action = action;
            r.success = true;
            r.reason = reason;
            return r;
        }

        public static RowResult fail(int rowNum, String sku, String reason) {
            RowResult r = new RowResult();
            r.rowNum = rowNum;
            r.sku = sku;
            r.action = "FAILED";
            r.success = false;
            r.reason = reason;
            return r;
        }

        public static RowResult skip(int rowNum, String sku, String reason) {
            RowResult r = new RowResult();
            r.rowNum = rowNum;
            r.sku = sku;
            r.action = "SKIPPED";
            r.success = true;
            r.reason = reason;
            return r;
        }
    }
}
