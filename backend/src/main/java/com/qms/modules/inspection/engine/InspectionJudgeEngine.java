package com.qms.modules.inspection.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.modules.standard.entity.NetContentTolerance;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import com.qms.modules.standard.mapper.NetContentToleranceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检验判定引擎（核心合规逻辑）。
 *
 * 单项规则：
 * - QUALITATIVE 定性：PASS/FAIL 直接映射，NA/空 不判定；
 * - QUANTITATIVE 定量：优先 JJF1070 净含量短缺量规则，其次 judge_config 操作符
 *   （GT/LT/GE/LE/EQ/BETWEEN/NOT_DETECT），最后 min/max 区间（单侧只校验存在侧）；
 * - DOCUMENT 资料：外检报告等附件已上传为 PASS，缺失为 FAIL。
 *
 * 综合规则：
 * - A 类（含一票否决）有不合格 → UNQUALIFIED，禁止让步、禁止复核改判；
 * - B/C 类不合格数超过模板允许值 → UNQUALIFIED；
 * - 限值内存在 B/C 不合格 → 建议 QUALIFIED，模板允许让步时可由复核人提 CONCESSION。
 *
 * 核心判定为纯静态方法，可脱离 Spring 直接单元测试全分支。
 */
@Component
@RequiredArgsConstructor
public class InspectionJudgeEngine {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String NONE = "NONE";

    private final NetContentToleranceMapper toleranceMapper;

    /** 按标示净含量解析 JJF1070 分档（min 开区间、max 闭区间） */
    public NetContentTolerance resolveTolerance(BigDecimal nominal) {
        return toleranceMapper.selectOne(new LambdaQueryWrapper<NetContentTolerance>()
                .eq(NetContentTolerance::getUnitScope, "G_ML")
                .lt(NetContentTolerance::getMinQty, nominal)
                .and(w -> w.isNull(NetContentTolerance::getMaxQty)
                        .or().ge(NetContentTolerance::getMaxQty, nominal))
                .orderByAsc(NetContentTolerance::getSort)
                .last("LIMIT 1"));
    }

    // ------------------------------------------------------------------
    // 单项判定（纯逻辑）
    // ------------------------------------------------------------------

    public static JudgeItemResult judgeItem(StandardItem item, String qualitative,
                                            BigDecimal quantitative, Long docAttachmentId,
                                            NetContentTolerance tolerance) {
        return judgeItem(item, qualitative, quantitative, docAttachmentId, tolerance,
                item == null ? null : item.getNominalValue());
    }

    /**
     * @param effectiveNetNominal 净含量 JJF1070 判定时的有效标称值；
     *                            检验项未配置 nominal_value 时由任务样品的 SKU 净含量提供
     */
    public static JudgeItemResult judgeItem(StandardItem item, String qualitative,
                                            BigDecimal quantitative, Long docAttachmentId,
                                            NetContentTolerance tolerance,
                                            BigDecimal effectiveNetNominal) {
        Map<String, Object> snapshot = baseSnapshot(item);
        return switch (item.getResultType()) {
            case "QUALITATIVE" -> judgeQualitative(qualitative, snapshot);
            case "QUANTITATIVE" -> judgeQuantitative(item, quantitative, tolerance, effectiveNetNominal, snapshot);
            case "DOCUMENT" -> judgeDocument(docAttachmentId, snapshot);
            default -> new JudgeItemResult(NONE, "未知结果类型", snapshot);
        };
    }

    private static JudgeItemResult judgeQualitative(String qualitative, Map<String, Object> snapshot) {
        if (qualitative == null || qualitative.isBlank() || "NA".equals(qualitative)) {
            return new JudgeItemResult(NONE, "未录入定性结果", snapshot);
        }
        return switch (qualitative) {
            case PASS -> new JudgeItemResult(PASS, "符合", snapshot);
            case FAIL -> new JudgeItemResult(FAIL, "不符合", snapshot);
            default -> new JudgeItemResult(NONE, "非法定性值: " + qualitative, snapshot);
        };
    }

    private static JudgeItemResult judgeQuantitative(StandardItem item, BigDecimal value,
                                                     NetContentTolerance tolerance,
                                                     BigDecimal effectiveNetNominal,
                                                     Map<String, Object> snapshot) {
        if (value == null) {
            return new JudgeItemResult(NONE, "未录入实测值", snapshot);
        }
        // 1) JJF1070 净含量短缺量（标称值取检验项配置，缺省由 SKU 净含量提供）
        if ("JJF1070".equalsIgnoreCase(item.getToleranceRule())
                && effectiveNetNominal != null) {
            return judgeByJJF1070(item, value, tolerance, effectiveNetNominal, snapshot);
        }
        // 2) judge_config 操作符
        Map<String, Object> cfg = item.getJudgeConfig();
        if (cfg != null && cfg.get("operator") != null) {
            return judgeByOperator(value, cfg, snapshot);
        }
        // 3) min/max 区间（单侧只校验存在侧）
        BigDecimal min = item.getMinValue();
        BigDecimal max = item.getMaxValue();
        snapshot.put("min", min);
        snapshot.put("max", max);
        if (min != null && value.compareTo(min) < 0) {
            return new JudgeItemResult(FAIL, "实测值 " + value + " 低于下限 " + min, snapshot);
        }
        if (max != null && value.compareTo(max) > 0) {
            return new JudgeItemResult(FAIL, "实测值 " + value + " 高于上限 " + max, snapshot);
        }
        if (min == null && max == null) {
            return new JudgeItemResult(NONE, "未配置判定阈值，仅记录实测值", snapshot);
        }
        return new JudgeItemResult(PASS, "在标准区间内", snapshot);
    }

    /**
     * JJF1070：单件商品实测净含量 ≥ 标示值 ×(1−T)。
     * PERCENT：T 为百分比；ABSOLUTE：T 为允许短缺绝对量。
     */
    private static JudgeItemResult judgeByJJF1070(StandardItem item, BigDecimal measured,
                                                  NetContentTolerance tolerance,
                                                  BigDecimal nominal,
                                                  Map<String, Object> snapshot) {
        snapshot.put("rule", "JJF1070");
        snapshot.put("nominal", nominal);
        snapshot.put("unit", item.getUnit());
        if (tolerance == null) {
            return new JudgeItemResult(NONE, "未匹配到JJF1070短缺量分档，无法自动判定", snapshot);
        }
        BigDecimal allowed;
        if ("PERCENT".equals(tolerance.getShortageType())) {
            allowed = nominal.multiply(BigDecimal.ONE.subtract(
                    tolerance.getShortageValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        } else {
            allowed = nominal.subtract(tolerance.getShortageValue());
        }
        allowed = allowed.setScale(4, RoundingMode.HALF_UP);
        snapshot.put("tierMinQty", tolerance.getMinQty());
        snapshot.put("tierMaxQty", tolerance.getMaxQty());
        snapshot.put("shortageType", tolerance.getShortageType());
        snapshot.put("shortageValue", tolerance.getShortageValue());
        snapshot.put("minAllowed", allowed);
        String unit = item.getUnit() == null ? "" : item.getUnit();
        if (measured.compareTo(allowed) < 0) {
            return new JudgeItemResult(FAIL,
                    "实测净含量 " + measured + unit + " 低于允许短缺量下限 " + allowed, snapshot);
        }
        return new JudgeItemResult(PASS, "净含量符合JJF1070允许短缺量要求", snapshot);
    }

    private static JudgeItemResult judgeByOperator(BigDecimal value, Map<String, Object> cfg,
                                                   Map<String, Object> snapshot) {
        String operator = String.valueOf(cfg.get("operator")).toUpperCase();
        BigDecimal threshold = decimal(cfg.get("value"));
        BigDecimal threshold2 = decimal(cfg.get("value2"));
        snapshot.put("operator", operator);
        snapshot.put("value", threshold);
        snapshot.put("value2", threshold2);
        boolean ok = switch (operator) {
            case "GT" -> threshold != null && value.compareTo(threshold) > 0;
            case "GE" -> threshold != null && value.compareTo(threshold) >= 0;
            case "LT" -> threshold != null && value.compareTo(threshold) < 0;
            case "LE" -> threshold != null && value.compareTo(threshold) <= 0;
            case "EQ" -> threshold != null && value.compareTo(threshold) == 0;
            case "BETWEEN" -> threshold != null && threshold2 != null
                    && value.compareTo(threshold) >= 0 && value.compareTo(threshold2) <= 0;
            // 未检出/低于检出限：实测值 ≤ 限值视为合格
            case "NOT_DETECT" -> threshold != null && value.compareTo(threshold) <= 0;
            default -> false;
        };
        if (threshold == null || (operator.equals("BETWEEN") && threshold2 == null)) {
            return new JudgeItemResult(NONE, "judge_config 阈值配置不完整", snapshot);
        }
        return ok
                ? new JudgeItemResult(PASS, "满足判定规则 " + operator, snapshot)
                : new JudgeItemResult(FAIL, "实测值 " + value + " 不满足规则 " + operator, snapshot);
    }

    private static JudgeItemResult judgeDocument(Long docAttachmentId, Map<String, Object> snapshot) {
        snapshot.put("attachmentUploaded", docAttachmentId != null);
        if (docAttachmentId == null) {
            return new JudgeItemResult(FAIL, "缺少资料/外检报告附件", snapshot);
        }
        return new JudgeItemResult(PASS, "资料已上传", snapshot);
    }

    private static Map<String, Object> baseSnapshot(StandardItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("itemId", item.getId());
        snapshot.put("itemName", item.getItemName());
        snapshot.put("groupCode", item.getGroupCode());
        snapshot.put("resultType", item.getResultType());
        snapshot.put("defectLevel", item.getDefectLevel());
        snapshot.put("veto", item.getVetoFlag());
        snapshot.put("required", item.getRequiredFlag());
        snapshot.put("inspectMethod", item.getInspectMethod());
        return snapshot;
    }

    private static BigDecimal decimal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(String.valueOf(obj));
    }

    // ------------------------------------------------------------------
    // 综合判定（纯逻辑）
    // ------------------------------------------------------------------

    /**
     * @param items       模板检验项
     * @param judgements  itemId → 单项最终判定（PASS/FAIL/NONE）
     */
    public static JudgeSummary summarize(List<StandardItem> items,
                                         Map<Long, String> judgements,
                                         StandardTemplate template) {
        JudgeSummary summary = new JudgeSummary();
        for (StandardItem item : items) {
            String judgement = judgements.getOrDefault(item.getId(), NONE);
            if (!FAIL.equals(judgement)) {
                continue;
            }
            boolean veto = item.getVetoFlag() != null && item.getVetoFlag() == 1;
            switch (item.getDefectLevel()) {
                case "A" -> {
                    summary.setAFailCount(summary.getAFailCount() + 1);
                    if (veto) {
                        summary.setVetoFail(true);
                    }
                }
                case "B" -> summary.setBFailCount(summary.getBFailCount() + 1);
                case "C" -> summary.setCFailCount(summary.getCFailCount() + 1);
                default -> { }
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("itemId", item.getId());
            detail.put("groupCode", item.getGroupCode());
            detail.put("itemName", item.getItemName());
            detail.put("defectLevel", item.getDefectLevel());
            detail.put("veto", veto);
            summary.getFailures().add(detail);
        }

        int maxA = template.getMaxAFail() == null ? 0 : template.getMaxAFail();
        int maxB = template.getMaxBFail() == null ? 0 : template.getMaxBFail();
        int maxC = template.getMaxCFail() == null ? 0 : template.getMaxCFail();

        boolean aExceed = summary.getAFailCount() > maxA;
        boolean bExceed = summary.getBFailCount() > maxB;
        boolean cExceed = summary.getCFailCount() > maxC;
        boolean unqualified = summary.isVetoFail() || aExceed || bExceed || cExceed;
        summary.setUnqualified(unqualified);

        int totalFail = summary.getAFailCount() + summary.getBFailCount() + summary.getCFailCount();
        boolean concessionAllowed = template.getConcessionAllowed() != null
                && template.getConcessionAllowed() == 1;
        summary.setConcessionPossible(concessionAllowed && !unqualified && totalFail > 0);
        summary.setSuggestedConclusion(unqualified ? "UNQUALIFIED" : "QUALIFIED");
        return summary;
    }
}
