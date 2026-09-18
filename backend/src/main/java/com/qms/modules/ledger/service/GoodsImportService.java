package com.qms.modules.ledger.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.AuditLog;
import com.qms.modules.ledger.LedgerConst;
import com.qms.modules.ledger.dto.GoodsImportRow;
import com.qms.modules.ledger.dto.GoodsUpsertRequest;
import com.qms.modules.ledger.dto.NodeRecordUpsertRequest;
import com.qms.modules.ledger.entity.LedgerGoods;
import com.qms.modules.ledger.mapper.LedgerGoodsMapper;
import com.qms.modules.ledger.vo.GoodsImportResultVO;
import com.qms.modules.masterdata.entity.Category;
import com.qms.modules.masterdata.entity.Supplier;
import com.qms.modules.masterdata.mapper.CategoryMapper;
import com.qms.modules.masterdata.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品台账 Excel 批量导入与标准模板下载。
 * 设计要点：
 * 1) EasyExcel SAX 流式读取 + 200 行分批处理，大文件不会一次性载入内存；
 * 2) 每一行在独立事务中提交，单行失败只回滚该行，结果页逐行给出原因；
 * 3) 按 SKU 匹配：不存在新建（标记 USER），已存在按策略更新/跳过；
 * 4) 供应商按名称精确匹配，不存在自动新建为合格供应商（USER）；
 * 5) 节点“结论文本”非空即办结，含“不通过”记为不通过；当前节点导入后自动重算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsImportService {

    /** 流式读取分批大小：攒满即处理并释放行对象 */
    private static final int BATCH_SIZE = 200;
    /** 模板数据工作表名（读取时优先按名匹配，兼容用户删掉说明页后下标前移） */
    private static final String DATA_SHEET_NAME = "商品导入模板";

    /** 模板表头（顺序即列下标，与 GoodsImportRow 严格一致） */
    private static final List<String> HEADERS = List.of(
            "SKU（必填，租户内唯一）",
            "产品通用名（必填）",
            "规格",
            "生产企业",
            "批准文号/备案号",
            "UPC(69码)",
            "品牌",
            "一级品类（填品类名称）",
            "二级品类（填品类名称，可空）",
            "过会时间(YYYY-MM-DD)",
            "合作结论（是/待定/否，留空按待定）",
            "上市日期(YYYY-MM-DD)",
            "准入供应商名称（不存在将自动新建）",
            "验厂供应商名称（不存在将自动新建）",
            "过会结论（填写即视为该节点已完成；含“不通过”视为不通过）",
            "准入审核完成日期(YYYY-MM-DD)",
            "供应商准入审核结论",
            "实地验厂完成日期(YYYY-MM-DD)",
            "实地验厂结论",
            "包装审核完成日期(YYYY-MM-DD)",
            "包装审核结论",
            "上市结论",
            "上市后监控完成日期(YYYY-MM-DD)",
            "上市后质量监控结论",
            "当前节点（导入时自动计算，此列留空即可）",
            "备注");

    private final LedgerGoodsService goodsService;
    private final LedgerFlowNodeService flowNodeService;
    private final LedgerGoodsMapper goodsMapper;
    private final SupplierMapper supplierMapper;
    private final CategoryMapper categoryMapper;
    private final TransactionTemplate transactionTemplate;

    // ============================ 模板下载 ============================

    /** 标准导入模板：填写说明 + 商品导入模板（含 2 行可删除的示例行） */
    public byte[] templateBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .build();
        try {
            WriteSheet instructionSheet = EasyExcel.writerSheet(0, "填写说明")
                    .head(List.of(List.of("填写项"), List.of("说明"))).build();
            writer.write(instructionRows(), instructionSheet);

            WriteSheet dataSheet = EasyExcel.writerSheet(1, DATA_SHEET_NAME)
                    .head(HEADERS.stream().map(List::of).collect(Collectors.toList())).build();
            writer.write(exampleRows(), dataSheet);
        } finally {
            writer.finish();
        }
        return out.toByteArray();
    }

    private List<List<Object>> instructionRows() {
        List<List<Object>> rows = new ArrayList<>();
        add(rows, "整体说明", "首行为表头，请勿增删列或调整列顺序；从第 2 行起按行填写。单文件建议不超过 1 万行。");
        add(rows, "示例行", "“商品导入模板”页前两行为示例（SKU 以“示例-”开头），正式导入前请删除或覆盖；系统也会自动跳过该行。");
        add(rows, "匹配规则", "按 SKU 精确匹配：不存在则新建，已存在按导入弹窗所选策略“更新已有/跳过已有”处理（默认更新）。");
        add(rows, "必填项", "SKU、产品通用名为必填；SKU 最长 50 字符且租户内唯一，删除后的 SKU 不可复用。");
        add(rows, "日期格式", "统一 YYYY-MM-DD（如 2026-03-01），也兼容 2026/3/1；Excel 数值日期单元格可自动识别。");
        add(rows, "合作结论", "只能填：是 / 待定 / 否；留空按“待定”处理。");
        add(rows, "品类", "一级/二级品类填写系统已有品类的“名称”；填了二级品类时必须属于所填一级品类，可只填一级。");
        add(rows, "供应商", "按名称精确匹配现有供应商；不存在时自动新建为“合格”供应商（数据来源标记为用户数据）。");
        add(rows, "节点结论",
                "某节点结论非空即视为该节点已完成，必须同时填写其完成日期（过会取“过会时间”、上市取“上市日期”）；"
                        + "结论中包含“不通过”记为“不通过”，其后节点结论必须留空；节点须按顺序填写，不得跨节点。");
        add(rows, "当前节点", "无需填写，导入后系统按各节点状态自动计算当前节点并在台账中展示。");
        add(rows, "数据来源", "导入与录入数据统一标记为“用户数据(USER)”，不会被“一键清除演示数据”删除。");
        return rows;
    }

    private void add(List<List<Object>> rows, String item, String desc) {
        rows.add(List.of(item, desc));
    }

    private List<List<Object>> exampleRows() {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of(
                "示例-001", "演示玫瑰花茶（示例）", "3g×20包", "样板花茶科技有限公司",
                "示例代用茶备字20250099号", "6900000009999", "样板生活",
                "花茶", "玫瑰花茶", "2026-01-10", "是", "2026-03-15",
                "样板花茶科技有限公司", "样板花茶科技有限公司",
                "会议表决通过", "2026-01-20", "资质齐全，准入通过",
                "2026-02-05", "现场审核通过（88分）",
                "2026-02-25", "包装与标签确认通过",
                "首批铺货完成", "2026-04-20", "上市监控正常",
                "", "示例行：正式导入前请删除本行")));
        rows.add(new ArrayList<>(List.of(
                "示例-002", "虚构黄芪饮片（示例）", "250g/袋", "虚构堂中药饮片有限公司",
                "示例饮片备字20250098号", "6900000009998", "虚构堂",
                "中药饮片", "", "2026-04-01", "待定", "",
                "虚构堂中药饮片有限公司", "虚构堂中药饮片有限公司",
                "会议表决通过", "2026-04-10", "资质齐全，准入通过",
                "2026-04-22", "现场审核通过（85分）",
                "2026-05-06", "包装与标签确认通过",
                "", "", "",
                "", "示例行：正式导入前请删除本行")));
        return rows;
    }

    // ============================ 导入解析 ============================

    /**
     * 流式导入。strategy=UPDATE（默认）更新已有 SKU；SKIP 跳过已有 SKU。
     */
    @AuditLog(module = "商品台账", action = "IMPORT", bizType = "qc_ledger_goods")
    public GoodsImportResultVO importExcel(InputStream inputStream, String strategy) {
        boolean skipExisting = "SKIP".equalsIgnoreCase(strategy == null ? "" : strategy.trim());
        GoodsImportResultVO result = new GoodsImportResultVO();

        // 整批导入共享的缓存：品类/供应商只查一次，同一文件内新建供应商不重复建
        ImportContext ctx = new ImportContext();
        ctx.skipExisting = skipExisting;
        ctx.categoryMap = loadEnabledCategories();
        ctx.supplierMap = loadSuppliers();

        AnalysisEventListener<GoodsImportRow> listener = new AnalysisEventListener<>() {
            private final List<GoodsImportRow> buffer = new ArrayList<>(BATCH_SIZE);

            @Override
            public void invoke(GoodsImportRow row, AnalysisContext context) {
                int rowNum = context.readRowHolder().getRowIndex() + 1;
                buffer.add(row);
                if (buffer.size() >= BATCH_SIZE) {
                    flush(context.readRowHolder().getRowIndex() + 1);
                }
            }

            private void flush(int endRowNum) {
                int startRowNum = endRowNum - buffer.size() + 1;
                List<GoodsImportRow> snapshot = new ArrayList<>(buffer);
                buffer.clear();
                for (int i = 0; i < snapshot.size(); i++) {
                    processRow(snapshot.get(i), startRowNum + i, ctx, result);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                if (!buffer.isEmpty()) {
                    flush(context.readRowHolder().getRowIndex() + 1);
                }
            }
        };

        ExcelReader reader = EasyExcel.read(inputStream, GoodsImportRow.class, listener)
                .autoCloseStream(true).build();
        try {
            List<ReadSheet> sheets = reader.excelExecutor().sheetList();
            ReadSheet target = pickDataSheet(sheets);
            reader.read(target);
        } finally {
            reader.finish();
        }
        log.info("商品Excel导入完成：总计{}，新建{}，更新{}，跳过{}，失败{}",
                result.getTotal(), result.getCreatedCount(), result.getUpdatedCount(),
                result.getSkippedCount(), result.getFailedCount());
        return result;
    }

    private ReadSheet pickDataSheet(List<ReadSheet> sheets) {
        ReadSheet byName = null;
        for (ReadSheet sheet : sheets) {
            if (DATA_SHEET_NAME.equals(sheet.getSheetName())) {
                byName = sheet;
                break;
            }
        }
        ReadSheet base = byName != null ? byName
                : sheets.size() > 1 ? sheets.get(1) : sheets.get(0);
        return EasyExcel.readSheet(base.getSheetNo()).sheetName(base.getSheetName())
                .head(GoodsImportRow.class).headRowNumber(1).build();
    }

    // ============================ 单行处理（独立事务） ============================

    private void processRow(GoodsImportRow row, int rowNum, ImportContext ctx,
                            GoodsImportResultVO result) {
        String sku = trim(row.getSku());
        // 空行与示例行不进结果统计，保持结果页只反映真实导入内容
        if (isBlankRow(row)) {
            return;
        }
        if (sku != null && sku.startsWith("示例-")) {
            return;
        }
        if (sku == null) {
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, "SKU 为空"));
            return;
        }
        String commonName = trim(row.getCommonName());
        if (commonName == null) {
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, "产品通用名为空"));
            return;
        }
        if (sku.length() > 50) {
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, "SKU 最长 50 个字符"));
            return;
        }
        if (commonName.length() > 200) {
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, "产品通用名最长 200 个字符"));
            return;
        }

        final ParsedRow parsed;
        try {
            parsed = parseRow(row, ctx);
        } catch (BizException e) {
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, e.getMessage()));
            return;
        } catch (Exception e) {
            log.warn("导入行解析失败 row={} sku={}", rowNum, sku, e);
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, "内容解析失败：" + e.getMessage()));
            return;
        }

        try {
            String action = transactionTemplate.execute(status -> upsertOne(parsed, ctx));
            String reason = "CREATED".equals(action) ? "新建成功"
                    : "UPDATED".equals(action) ? "已更新"
                    : "SKU 已存在，按策略跳过";
            if ("SKIPPED".equals(action)) {
                result.addRow(GoodsImportResultVO.RowResult.skip(rowNum, sku, reason));
            } else {
                result.addRow(GoodsImportResultVO.RowResult.ok(rowNum, sku, action, reason));
            }
        } catch (BizException e) {
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku, e.getMessage()));
        } catch (Exception e) {
            log.warn("导入行落库失败 row={} sku={}", rowNum, sku, e);
            result.addRow(GoodsImportResultVO.RowResult.fail(rowNum, sku,
                    "落库失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
        }
    }

    /** 新建或更新单个商品并同步已填写的流程节点；整个方法在单行独立事务中运行 */
    private String upsertOne(ParsedRow parsed, ImportContext ctx) {
        GoodsUpsertRequest req = new GoodsUpsertRequest();
        req.setSku(parsed.sku);
        req.setCommonName(parsed.commonName);
        req.setSpec(parsed.spec);
        req.setManufacturer(parsed.manufacturer);
        req.setApprovalNo(parsed.approvalNo);
        req.setUpc(parsed.upc);
        req.setBrand(parsed.brand);
        req.setCategoryL1Id(parsed.l1 == null ? null : parsed.l1.getId());
        req.setCategoryL2Id(parsed.l2 == null ? null : parsed.l2.getId());
        req.setMeetingDate(parsed.meetingDate);
        req.setCooperateResult(parsed.cooperateResult);
        req.setLaunchDate(parsed.launchDate);
        req.setRemark(parsed.remark);

        Long goodsId;
        String action;
        LedgerGoods existing = goodsMapper.selectOne(new LambdaQueryWrapper<LedgerGoods>()
                .eq(LedgerGoods::getSku, parsed.sku));
        if (existing == null) {
            // SKU 墓碑（已删记录）存在时 createInternal 会抛“SKU 不可复用”，这里交由统一异常出口
            goodsId = goodsService.createInternal(req, LedgerConst.SOURCE_USER);
            action = "CREATED";
        } else if (ctx.skipExisting) {
            return "SKIPPED";
        } else {
            req.setId(existing.getId());
            goodsService.update(req);
            goodsId = existing.getId();
            action = "UPDATED";
        }

        // 按节点顺序同步结论文本（服务端统一做必填/顺序/当前节点推进校验）
        for (NodeInput ni : parsed.nodes) {
            if (ni == null) {
                continue;
            }
            NodeRecordUpsertRequest nodeReq = new NodeRecordUpsertRequest();
            nodeReq.setStatus(ni.rejected ? LedgerConst.NODE_REJECTED : LedgerConst.NODE_DONE);
            nodeReq.setFinishDate(ni.finishDate);
            nodeReq.setConclusion(ni.conclusion);
            if (ni.supplierName != null) {
                nodeReq.setSupplierId(resolveSupplier(ni.supplierName, ctx));
            }
            flowNodeService.updateNode(goodsId, ni.nodeCode, nodeReq);
        }
        return action;
    }

    // ============================ 行内容解析 ============================

    private ParsedRow parseRow(GoodsImportRow row, ImportContext ctx) {
        ParsedRow p = new ParsedRow();
        p.sku = trim(row.getSku());
        p.commonName = trim(row.getCommonName());
        p.spec = trim(row.getSpec());
        p.manufacturer = trim(row.getManufacturer());
        p.approvalNo = trim(row.getApprovalNo());
        p.upc = trim(row.getUpc());
        p.brand = trim(row.getBrand());
        p.remark = trim(row.getRemark());
        p.meetingDate = parseDate(row.getMeetingDate(), "过会时间");
        p.launchDate = parseDate(row.getLaunchDate(), "上市日期");
        p.cooperateResult = parseCooperateResult(row.getCooperateResult());

        // 品类：按名称精确匹配启用中的品类
        String l1Name = trim(row.getCategoryL1Name());
        String l2Name = trim(row.getCategoryL2Name());
        if (l1Name != null) {
            p.l1 = ctx.categoryMap.get(l1Name);
            if (p.l1 == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "一级品类不存在或已停用：" + l1Name);
            }
        }
        if (l2Name != null) {
            p.l2 = ctx.categoryMap.get(l2Name);
            if (p.l2 == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "二级品类不存在或已停用：" + l2Name);
            }
            if (p.l1 == null) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "填写了二级品类【" + l2Name + "】时必须同时填写一级品类");
            }
            if (p.l2.getParentId() == null || !p.l2.getParentId().equals(p.l1.getId())) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "二级品类【" + l2Name + "】不属于一级品类【" + l1Name + "】");
            }
        }

        // 供应商名称在单行事务内再解析：行内其他步骤失败时，自动新建的供应商随事务一并回滚
        LocalDate auditDate = parseDate(row.getAuditFinishDate(), "准入审核完成日期");
        LocalDate factoryDate = parseDate(row.getFactoryFinishDate(), "实地验厂完成日期");
        LocalDate packageDate = parseDate(row.getPackageFinishDate(), "包装审核完成日期");
        LocalDate postMarketDate = parseDate(row.getPostMarketFinishDate(), "上市后监控完成日期");

        p.nodes = new ArrayList<>();
        appendNode(p.nodes, "MEETING", "过会", row.getMeetingConclusion(), p.meetingDate,
                "过会结论已填写时，过会时间必填", null, true);
        appendNode(p.nodes, "SUPPLIER_AUDIT", "供应商准入审核", row.getAuditConclusion(), auditDate,
                "供应商准入结论已填写时，准入审核完成日期必填", trim(row.getAuditSupplierName()), false);
        appendNode(p.nodes, "FACTORY_AUDIT", "实地验厂", row.getFactoryConclusion(), factoryDate,
                "实地验厂结论已填写时，实地验厂完成日期必填", trim(row.getFactorySupplierName()), false);
        appendNode(p.nodes, "PACKAGE_REVIEW", "包装审核确认", row.getPackageConclusion(), packageDate,
                "包装审核结论已填写时，包装审核完成日期必填", null, true);
        appendNode(p.nodes, "LISTING", "上市", row.getListingConclusion(), p.launchDate,
                "上市结论已填写时，上市日期必填", null, true);
        appendNode(p.nodes, "POST_MARKET", "上市后质量监控", row.getPostMarketConclusion(), postMarketDate,
                "上市后监控结论已填写时，上市后监控完成日期必填", null, true);

        // 顺序校验：节点结论必须构成“连续前缀”，不通过之后不得再有办结节点
        boolean seenBlank = false;
        for (NodeInput ni : p.nodes) {
            if (ni == null) {
                seenBlank = true;
            } else if (seenBlank) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "流程节点需按“过会→供应商准入→实地验厂→包装审核→上市→上市后监控”顺序填写，不能跨节点");
            }
        }
        for (int i = 0; i < p.nodes.size(); i++) {
            NodeInput ni = p.nodes.get(i);
            if (ni != null && ni.rejected) {
                for (int j = i + 1; j < p.nodes.size(); j++) {
                    if (p.nodes.get(j) != null) {
                        throw new BizException(ResultCode.PARAM_INVALID,
                                "节点【" + ni.nodeName + "】为“不通过”时，后续节点结论必须留空");
                    }
                }
            }
        }
        return p;
    }

    private void appendNode(List<NodeInput> nodes, String nodeCode, String nodeName,
                            String rawConclusion, LocalDate finishDate, String missingDateReason,
                            String supplierName, boolean supplierNotApplicable) {
        String conclusion = trim(rawConclusion);
        if (conclusion == null) {
            nodes.add(null);
            return;
        }
        if (finishDate == null) {
            throw new BizException(ResultCode.PARAM_INVALID, missingDateReason);
        }
        NodeInput ni = new NodeInput();
        ni.nodeCode = nodeCode;
        ni.nodeName = nodeName;
        ni.conclusion = conclusion;
        ni.finishDate = finishDate;
        ni.supplierName = supplierNotApplicable ? null : supplierName;
        ni.rejected = conclusion.contains("不通过");
        nodes.add(ni);
    }

    private Long resolveSupplier(String rawName, ImportContext ctx) {
        String name = trim(rawName);
        if (name == null) {
            return null;
        }
        Long cached = ctx.supplierMap.get(name);
        if (cached != null) {
            return cached;
        }
        Supplier supplier = new Supplier();
        supplier.setSupplierCode("SUP-IMP-" + IdWorker.getId());
        supplier.setSupplierName(name);
        supplier.setStatus("QUALIFIED");
        supplierMapper.insert(supplier);
        ctx.supplierMap.put(name, supplier.getId());
        return supplier.getId();
    }

    private Map<String, Category> loadEnabledCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, 1))
                .stream().collect(Collectors.toMap(Category::getName, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Long> loadSuppliers() {
        Map<String, Long> map = new HashMap<>();
        supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                        .select(Supplier::getId, Supplier::getSupplierName))
                .forEach(s -> map.putIfAbsent(s.getSupplierName(), s.getId()));
        return map;
    }

    private String parseCooperateResult(String raw) {
        String v = trim(raw);
        if (v == null) {
            return LedgerConst.RESULT_PENDING;
        }
        return switch (v) {
            case "是" -> LedgerConst.RESULT_YES;
            case "否" -> LedgerConst.RESULT_NO;
            case "待定" -> LedgerConst.RESULT_PENDING;
            case LedgerConst.RESULT_YES, LedgerConst.RESULT_NO, LedgerConst.RESULT_PENDING -> v;
            default -> throw new BizException(ResultCode.PARAM_INVALID,
                    "合作结论只能填“是/待定/否”，实际为：" + v);
        };
    }

    /**
     * 日期解析：yyyy-MM-dd / yyyy/M/d；纯数字按 Excel 1900 日期序列（基准 1899-12-30）换算。
     */
    private LocalDate parseDate(String raw, String label) {
        String v = trim(raw);
        if (v == null) {
            return null;
        }
        try {
            if (v.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                return LocalDate.parse(v.replace('/', '-'));
            }
            if (v.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
                String[] parts = v.split("/");
                return LocalDate.of(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            if (v.matches("\\d+(\\.\\d+)?")) {
                long serial = (long) Math.floor(Double.parseDouble(v));
                return LocalDate.of(1899, 12, 30).plusDays(serial);
            }
        } catch (Exception ignore) {
            // 落到统一异常
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                label + "日期格式不正确（应为 YYYY-MM-DD）：" + v);
    }

    private boolean isBlankRow(GoodsImportRow r) {
        return trim(r.getSku()) == null && trim(r.getCommonName()) == null
                && trim(r.getManufacturer()) == null && trim(r.getBrand()) == null
                && trim(r.getMeetingConclusion()) == null;
    }

    private String trim(String v) {
        if (v == null) {
            return null;
        }
        // 常见 BOM 与不间断空格清理（避免源码中出现裸 unicode 转义，用码点构造特殊字符）
        String t = v.replace((char) 0xFEFF, ' ').replace((char) 0x00A0, ' ').trim();
        return t.isEmpty() ? null : t;
    }

    /** 单次导入过程上下文（品类/供应商缓存 + 策略） */
    private static class ImportContext {
        private boolean skipExisting;
        private Map<String, Category> categoryMap;
        private Map<String, Long> supplierMap;
    }

    private static class ParsedRow {
        private String sku;
        private String commonName;
        private String spec;
        private String manufacturer;
        private String approvalNo;
        private String upc;
        private String brand;
        private String remark;
        private Category l1;
        private Category l2;
        private LocalDate meetingDate;
        private LocalDate launchDate;
        private String cooperateResult;
        private List<NodeInput> nodes;
    }

    private static class NodeInput {
        private String nodeCode;
        private String nodeName;
        private String conclusion;
        private LocalDate finishDate;
        private String supplierName;
        private boolean rejected;
    }
}
