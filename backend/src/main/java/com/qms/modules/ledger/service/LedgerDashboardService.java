package com.qms.modules.ledger.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.ledger.dto.FlowRecordExportRow;
import com.qms.modules.ledger.dto.GoodsLedgerExportRow;
import com.qms.modules.ledger.dto.MaterialGapExportRow;
import com.qms.modules.ledger.mapper.LedgerDashboardMapper;
import com.qms.modules.ledger.vo.LedgerDashboardVO;
import com.qms.modules.ledger.vo.MaterialGapVO;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品品控工作台：统计卡片/分布图表/缺口明细 + 三类质量报表 Excel 导出。
 */
@Service
@RequiredArgsConstructor
public class LedgerDashboardService {

    private final LedgerDashboardMapper dashboardMapper;
    private final CategoryMapper categoryMapper;

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter VIEW_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 过会状态编码 → 图表/筛选文案（固定顺序，为 0 也展示，保证图表结构稳定） */
    private static final Map<String, String> COOPERATE_RESULT_NAMES = new LinkedHashMap<>();
    /** 当前节点编码 → 名称（用于筛选条件文案） */
    private static final Map<String, String> NODE_CODE_NAMES = new LinkedHashMap<>();

    static {
        COOPERATE_RESULT_NAMES.put("YES", "已过会（合作）");
        COOPERATE_RESULT_NAMES.put("PENDING", "待定");
        COOPERATE_RESULT_NAMES.put("NO", "不合作");
        NODE_CODE_NAMES.put("MEETING", "过会");
        NODE_CODE_NAMES.put("SUPPLIER_AUDIT", "供应商准入审核");
        NODE_CODE_NAMES.put("FACTORY_AUDIT", "实地验厂");
        NODE_CODE_NAMES.put("PACKAGE_REVIEW", "包装审核确认");
        NODE_CODE_NAMES.put("LISTING", "上市");
        NODE_CODE_NAMES.put("POST_MARKET", "上市后质量监控");
    }

    /** 工作台主页统计数据 */
    public LedgerDashboardVO summary() {
        LedgerDashboardVO vo = new LedgerDashboardVO();

        Map<String, Object> cardRow = dashboardMapper.selectCards();
        LedgerDashboardVO.Cards cards = new LedgerDashboardVO.Cards();
        cards.setGoodsTotal(num(cardRow.get("goodsTotal")));
        cards.setSupplierTotal(num(cardRow.get("supplierTotal")));
        cards.setFlowRecordTotal(num(cardRow.get("flowRecordTotal")));
        cards.setGapItemTotal(num(cardRow.get("gapItemTotal")));
        vo.setCards(cards);

        // 过会状态分布：按固定三档输出
        Map<String, Long> coopMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dashboardMapper.selectCooperateResultDist()) {
            coopMap.put(str(row.get("code")), num(row.get("value")));
        }
        List<LedgerDashboardVO.NameValue> coopDist = new ArrayList<>();
        COOPERATE_RESULT_NAMES.forEach((code, name) ->
                coopDist.add(new LedgerDashboardVO.NameValue(name, coopMap.getOrDefault(code, 0L))));
        vo.setCooperateResultDist(coopDist);

        vo.setCurrentNodeDist(toNameValues(dashboardMapper.selectCurrentNodeDist()));
        vo.setCategoryDist(toNameValues(dashboardMapper.selectCategoryDist()));
        return vo;
    }

    /** 资料重点缺口明细分页 */
    public IPage<MaterialGapVO> gapPage(long pageNo, long pageSize, String keyword) {
        IPage<MaterialGapVO> page = new Page<>(pageNo <= 0 ? 1 : pageNo, pageSize <= 0 ? 10 : pageSize);
        IPage<MaterialGapVO> result = dashboardMapper.selectGapPage(page, trimToNull(keyword));
        for (MaterialGapVO row : result.getRecords()) {
            row.setGapItems(splitGapItems(row.getGapItemsRaw()));
            row.setGapItemsRaw(null);
        }
        return result;
    }

    /** 商品台账（含流程总览）导出 */
    @AuditLog(module = "品控工作台", action = "EXPORT_GOODS_LEDGER", bizType = "ledger_dashboard")
    public byte[] exportGoods(String keyword, Long categoryL1Id, Long categoryL2Id,
                              String cooperateResult, String currentNodeCode) {
        List<GoodsLedgerExportRow> rows = dashboardMapper.selectGoodsForExport(
                trimToNull(keyword), categoryL1Id, categoryL2Id,
                trimToNull(cooperateResult), trimToNull(currentNodeCode));
        List<MetaRow> meta = baseMeta("商品品控台账（含流程总览）", rows.size());
        meta.add(new MetaRow("筛选条件", goodsFilterText(keyword, categoryL1Id, categoryL2Id,
                cooperateResult, currentNodeCode)));
        return writeWorkbook("商品台账", meta, GoodsLedgerExportRow.class, rows);
    }

    /** 全流程记录导出 */
    @AuditLog(module = "品控工作台", action = "EXPORT_FLOW_RECORDS", bizType = "ledger_dashboard")
    public byte[] exportFlowRecords() {
        List<FlowRecordExportRow> rows = dashboardMapper.selectFlowRecordsForExport();
        List<MetaRow> meta = baseMeta("商品全流程记录（商品 × 流程节点）", rows.size());
        meta.add(new MetaRow("筛选条件", "全部商品的全部流程节点记录"));
        return writeWorkbook("全流程记录", meta, FlowRecordExportRow.class, rows);
    }

    /** 资料缺口清单导出 */
    @AuditLog(module = "品控工作台", action = "EXPORT_MATERIAL_GAPS", bizType = "ledger_dashboard")
    public byte[] exportMaterialGaps() {
        List<MaterialGapExportRow> rows = dashboardMapper.selectGapsForExport();
        List<MetaRow> meta = baseMeta("资料重点缺口清单（缺失 / 待确认）", rows.size());
        meta.add(new MetaRow("筛选条件", "全部缺失与待确认资料项"));
        return writeWorkbook("资料缺口清单", meta, MaterialGapExportRow.class, rows);
    }

    /** 统一的双 Sheet 写法：Sheet0 导出说明（时间/筛选/行数），Sheet1 数据 */
    private <T> byte[] writeWorkbook(String dataSheetName, List<MetaRow> meta,
                                     Class<T> head, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = EasyExcel.write(out)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .build()) {
            WriteSheet metaSheet = EasyExcel.writerSheet(0, "导出说明").head(MetaRow.class).build();
            WriteSheet dataSheet = EasyExcel.writerSheet(1, dataSheetName).head(head).build();
            writer.write(meta, metaSheet);
            writer.write(rows, dataSheet);
        }
        return out.toByteArray();
    }

    private List<MetaRow> baseMeta(String reportName, int rowCount) {
        List<MetaRow> meta = new ArrayList<>();
        meta.add(new MetaRow("报表名称", reportName));
        meta.add(new MetaRow("导出时间", LocalDateTime.now().format(VIEW_TS)));
        meta.add(new MetaRow("数据行数", String.valueOf(rowCount)));
        return meta;
    }

    /** 商品台账筛选条件的中文描述（与列表页筛选项一致） */
    private String goodsFilterText(String keyword, Long categoryL1Id, Long categoryL2Id,
                                   String cooperateResult, String currentNodeCode) {
        List<String> parts = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            parts.add("关键字：" + keyword.trim());
        }
        if (categoryL1Id != null) {
            Category c = categoryMapper.selectById(categoryL1Id);
            parts.add("一级品类：" + (c == null ? String.valueOf(categoryL1Id) : c.getName()));
        }
        if (categoryL2Id != null) {
            Category c = categoryMapper.selectById(categoryL2Id);
            parts.add("二级品类：" + (c == null ? String.valueOf(categoryL2Id) : c.getName()));
        }
        if (cooperateResult != null && !cooperateResult.isBlank()) {
            parts.add("最终合作结论：" + COOPERATE_RESULT_NAMES.getOrDefault(cooperateResult, cooperateResult));
        }
        if (currentNodeCode != null && !currentNodeCode.isBlank()) {
            parts.add("当前节点：" + NODE_CODE_NAMES.getOrDefault(currentNodeCode, currentNodeCode));
        }
        return parts.isEmpty() ? "全部（无筛选）" : String.join("；", parts);
    }

    /** 拆分 GROUP_CONCAT 聚合串：资料项名|状态 以 ;; 分隔（资料项名/状态均由系统预置，不含分隔符） */
    private List<MaterialGapVO.GapItem> splitGapItems(String raw) {
        List<MaterialGapVO.GapItem> items = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return items;
        }
        for (String piece : raw.split(";;")) {
            int idx = piece.lastIndexOf('|');
            if (idx > 0) {
                items.add(new MaterialGapVO.GapItem(piece.substring(0, idx), piece.substring(idx + 1)));
            } else {
                items.add(new MaterialGapVO.GapItem(piece, "MISSING"));
            }
        }
        return items;
    }

    private List<LedgerDashboardVO.NameValue> toNameValues(List<Map<String, Object>> rows) {
        List<LedgerDashboardVO.NameValue> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(new LedgerDashboardVO.NameValue(str(row.get("name")), num(row.get("value"))));
        }
        return list;
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 导出说明 Sheet 行模型 */
    @Data
    @ColumnWidth(24)
    public static class MetaRow {
        @ExcelProperty("项目")
        @ColumnWidth(18)
        private String item;

        @ExcelProperty("内容")
        @ColumnWidth(60)
        private String value;

        public MetaRow() {
        }

        public MetaRow(String item, String value) {
            this.item = item;
            this.value = value;
        }
    }

    /** 文件名时间戳（供 Controller 命名用） */
    public static String fileTimestamp() {
        return LocalDateTime.now().format(FILE_TS);
    }
}
