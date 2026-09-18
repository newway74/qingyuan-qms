package com.qms.modules.inspection.engine;

import com.qms.modules.standard.entity.NetContentTolerance;
import com.qms.modules.standard.entity.StandardItem;
import com.qms.modules.standard.entity.StandardTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判定引擎纯逻辑全分支单测（不启动 Spring）。
 */
class InspectionJudgeEngineTest {

    private StandardItem item(String type, String level, int veto, int required) {
        StandardItem item = new StandardItem();
        item.setId(1L);
        item.setItemName("测试项");
        item.setGroupCode("SENSORY");
        item.setResultType(type);
        item.setDefectLevel(level);
        item.setVetoFlag(veto);
        item.setRequiredFlag(required);
        return item;
    }

    private NetContentTolerance tier(String type, String value) {
        NetContentTolerance t = new NetContentTolerance();
        t.setShortageType(type);
        t.setShortageValue(new BigDecimal(value));
        return t;
    }

    private StandardTemplate template(int maxA, int maxB, int maxC, int concession) {
        StandardTemplate t = new StandardTemplate();
        t.setMaxAFail(maxA);
        t.setMaxBFail(maxB);
        t.setMaxCFail(maxC);
        t.setConcessionAllowed(concession);
        return t;
    }

    @Nested
    @DisplayName("定性判定")
    class Qualitative {

        @Test
        void pass_fail_na() {
            StandardItem item = item("QUALITATIVE", "B", 0, 1);
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, "PASS", null, null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, "FAIL", null, null, null).getJudgement());
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, "NA", null, null, null).getJudgement());
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, null, null, null, null).getJudgement());
        }
    }

    @Nested
    @DisplayName("区间定量判定")
    class Range {

        @Test
        void within_and_out_of_range() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            item.setMinValue(new BigDecimal("10.00"));
            item.setMaxValue(new BigDecimal("20.00"));
            item.setUnit("%");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("15"), null, null).getJudgement());
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("10.00"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("9.99"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("20.01"), null, null).getJudgement());
        }

        @Test
        void single_sided_only_validates_existing_side() {
            StandardItem item = item("QUANTITATIVE", "C", 0, 1);
            item.setMinValue(new BigDecimal("0.5"));
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("999"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("0.4"), null, null).getJudgement());
        }

        @Test
        void missing_value_is_none() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            item.setMinValue(new BigDecimal("1"));
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, null, null, null, null).getJudgement());
        }
    }

    @Nested
    @DisplayName("JJF1070 净含量短缺量")
    class JJF1070 {

        @Test
        void absolute_tier_100g() {
            // 50 < Q <= 100，绝对短缺 4.5g，下限 95.5
            StandardItem item = item("QUANTITATIVE", "A", 1, 1);
            item.setToleranceRule("JJF1070");
            item.setNominalValue(new BigDecimal("100"));
            item.setUnit("g");
            NetContentTolerance t = tier("ABSOLUTE", "4.5");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("95.5"), null, t).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("95.49"), null, t).getJudgement());
        }

        @Test
        void absolute_tier_250g() {
            // 200 < Q <= 300，绝对短缺 9g，下限 241
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            item.setToleranceRule("JJF1070");
            item.setNominalValue(new BigDecimal("250"));
            item.setUnit("g");
            NetContentTolerance t = tier("ABSOLUTE", "9");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("241.0"), null, t).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("240"), null, t).getJudgement());
        }

        @Test
        void percent_tier_200g() {
            // 100 < Q <= 200，4.5%，下限 191
            StandardItem item = item("QUANTITATIVE", "C", 0, 1);
            item.setToleranceRule("JJF1070");
            item.setNominalValue(new BigDecimal("200"));
            item.setUnit("g");
            NetContentTolerance t = tier("PERCENT", "4.5");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("191.00"), null, t).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("190.99"), null, t).getJudgement());
        }

        @Test
        void percent_tier_50g() {
            // Q<=50，9%，下限 45.5
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            item.setToleranceRule("JJF1070");
            item.setNominalValue(new BigDecimal("50"));
            item.setUnit("g");
            NetContentTolerance t = tier("PERCENT", "9");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("45.5"), null, t).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("45"), null, t).getJudgement());
        }

        @Test
        void missing_tier_is_none() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            item.setToleranceRule("JJF1070");
            item.setNominalValue(new BigDecimal("250"));
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("240"), null, null).getJudgement());
        }
    }

    @Nested
    @DisplayName("judge_config 操作符")
    class Operators {

        private StandardItem operatorItem(String operator, String value, String value2) {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("operator", operator);
            cfg.put("value", value);
            if (value2 != null) {
                cfg.put("value2", value2);
            }
            item.setJudgeConfig(cfg);
            return item;
        }

        @Test
        void gt_lt_ge_le_eq() {
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(operatorItem("GT", "10", null), null, new BigDecimal("11"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(operatorItem("GT", "10", null), null, new BigDecimal("10"), null, null).getJudgement());
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(operatorItem("LE", "10", null), null, new BigDecimal("10"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(operatorItem("LT", "10", null), null, new BigDecimal("10"), null, null).getJudgement());
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(operatorItem("GE", "10", null), null, new BigDecimal("10"), null, null).getJudgement());
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(operatorItem("EQ", "10.0", null), null, new BigDecimal("10.00"), null, null).getJudgement());
        }

        @Test
        void between() {
            StandardItem item = operatorItem("BETWEEN", "1.0", "2.0");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("1.5"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("2.1"), null, null).getJudgement());
            // BETWEEN 缺 value2 → NONE
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(operatorItem("BETWEEN", "1.0", null),
                    null, new BigDecimal("1.5"), null, null).getJudgement());
        }

        @Test
        void not_detect_means_value_below_limit() {
            // 二氧化硫未检出：实测 ≤ 检出限 0.01 为合格
            StandardItem item = operatorItem("NOT_DETECT", "0.01", null);
            item.setUnit("g/kg");
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("0.005"), null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("0.02"), null, null).getJudgement());
        }
    }

    @Nested
    @DisplayName("资料项")
    class Document {

        @Test
        void attachment_present_or_missing() {
            StandardItem item = item("DOCUMENT", "A", 1, 1);
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, null, 99L, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, null, null, null).getJudgement());
        }
    }

    @Nested
    @DisplayName("综合判定")
    class Summary {

        private StandardItem item(long id, String level, int veto) {
            StandardItem item = new StandardItem();
            item.setId(id);
            item.setResultType("QUALITATIVE");
            item.setDefectLevel(level);
            item.setVetoFlag(veto);
            item.setRequiredFlag(1);
            return item;
        }

        @Test
        void all_pass_qualified() {
            StandardItem a = item(1, "A", 1);
            StandardItem b = item(2, "B", 0);
            Map<Long, String> j = Map.of(1L, "PASS", 2L, "PASS");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(a, b), j, template(0, 0, 0, 1));
            assertFalse(s.isUnqualified());
            assertFalse(s.isVetoFail());
            assertFalse(s.isConcessionPossible());
            assertEquals("QUALIFIED", s.getSuggestedConclusion());
        }

        @Test
        void veto_a_fail_forces_unqualified_and_blocks_concession() {
            StandardItem a = item(1, "A", 1);
            StandardItem b = item(2, "B", 0);
            Map<Long, String> j = Map.of(1L, "FAIL", 2L, "PASS");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(a, b), j, template(0, 0, 0, 1));
            assertTrue(s.isVetoFail());
            assertTrue(s.isUnqualified());
            assertFalse(s.isConcessionPossible());
            assertEquals(1, s.getAFailCount());
            assertEquals("UNQUALIFIED", s.getSuggestedConclusion());
        }

        @Test
        void b_fail_within_limit_qualified_and_concession_possible_when_allowed() {
            StandardItem b1 = item(1, "B", 0);
            StandardItem b2 = item(2, "B", 0);
            Map<Long, String> j = Map.of(1L, "FAIL", 2L, "PASS");
            JudgeSummary allowed = InspectionJudgeEngine.summarize(List.of(b1, b2), j, template(0, 2, 0, 1));
            assertFalse(allowed.isUnqualified());
            assertTrue(allowed.isConcessionPossible());
            assertEquals("QUALIFIED", allowed.getSuggestedConclusion());

            JudgeSummary notAllowed = InspectionJudgeEngine.summarize(List.of(b1, b2), j, template(0, 2, 0, 0));
            assertFalse(notAllowed.isConcessionPossible());
        }

        @Test
        void b_fail_exceeds_limit_unqualified() {
            StandardItem b1 = item(1, "B", 0);
            StandardItem b2 = item(2, "B", 0);
            StandardItem b3 = item(3, "B", 0);
            Map<Long, String> j = Map.of(1L, "FAIL", 2L, "FAIL", 3L, "PASS");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(b1, b2, b3), j, template(0, 1, 0, 1));
            assertEquals(2, s.getBFailCount());
            assertTrue(s.isUnqualified());
            assertFalse(s.isConcessionPossible());
        }

        @Test
        void c_fail_exceeds_limit_unqualified() {
            StandardItem c1 = item(1, "C", 0);
            StandardItem c2 = item(2, "C", 0);
            Map<Long, String> j = Map.of(1L, "FAIL", 2L, "FAIL");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(c1, c2), j, template(0, 0, 1, 1));
            assertEquals(2, s.getCFailCount());
            assertTrue(s.isUnqualified());
        }

        @Test
        void none_treated_as_non_fail() {
            StandardItem c = item(1, "C", 0);
            Map<Long, String> j = Map.of(1L, "NONE");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(c), j, template(0, 0, 0, 1));
            assertEquals(0, s.getCFailCount());
            assertFalse(s.isUnqualified());
        }

        @Test
        void a_fail_within_limit_non_veto_is_qualified_and_concession_possible() {
            StandardItem a = item(1, "A", 0);
            Map<Long, String> j = Map.of(1L, "PASS");
            // 1 个非否决 A 不合格，模板允许 1 个 A
            Map<Long, String> jf = Map.of(1L, "FAIL");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(a), jf, template(1, 0, 0, 1));
            assertEquals(1, s.getAFailCount());
            assertFalse(s.isVetoFail());
            assertFalse(s.isUnqualified());
            assertTrue(s.isConcessionPossible());
            assertEquals("QUALIFIED", s.getSuggestedConclusion());
            // 对照：同项 PASS 时不存在让步空间
            JudgeSummary p = InspectionJudgeEngine.summarize(List.of(a), j, template(1, 0, 0, 1));
            assertFalse(p.isConcessionPossible());
        }

        @Test
        void unknown_defect_level_fail_is_recorded_but_not_counted() {
            StandardItem weird = item(1, "X", 0);
            Map<Long, String> j = Map.of(1L, "FAIL");
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(weird), j, template(0, 0, 0, 1));
            assertEquals(0, s.getAFailCount() + s.getBFailCount() + s.getCFailCount());
            assertFalse(s.isUnqualified());
            assertEquals(1, s.getFailures().size());
        }

        @Test
        void null_template_limits_treated_as_zero() {
            StandardTemplate t = new StandardTemplate();
            StandardItem b = item(1, "B", 0);
            JudgeSummary s = InspectionJudgeEngine.summarize(List.of(b), Map.of(1L, "FAIL"), t);
            assertTrue(s.isUnqualified());
        }
    }

    @Nested
    @DisplayName("其他边界分支")
    class EdgeBranches {

        @Test
        void unknown_result_type_is_none() {
            StandardItem item = item("BOOLEAN", "B", 0, 1);
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, null, null, null, null).getJudgement());
        }

        @Test
        void illegal_qualitative_value_is_none() {
            StandardItem item = item("QUALITATIVE", "B", 0, 1);
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, "MAYBE", null, null, null).getJudgement());
        }

        @Test
        void quantitative_without_any_threshold_is_none() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("3.14"), null, null).getJudgement());
        }

        @Test
        void unknown_operator_fails() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("operator", "REGEX");
            cfg.put("value", "1");
            item.setJudgeConfig(cfg);
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("1"), null, null).getJudgement());
        }

        @Test
        void operator_without_value_threshold_is_none() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("operator", "GT");
            item.setJudgeConfig(cfg);
            assertEquals("NONE", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("1"), null, null).getJudgement());
        }

        @Test
        void jjf1070_without_effective_nominal_falls_back_to_range() {
            StandardItem item = item("QUANTITATIVE", "B", 0, 1);
            item.setToleranceRule("JJF1070");
            item.setMinValue(new BigDecimal("1"));
            item.setMaxValue(new BigDecimal("2"));
            // 无标称值（项与 SKU 均未提供）→ JJF1070 不生效，回落区间判定
            assertEquals("PASS", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("1.5"), null, null, null).getJudgement());
            assertEquals("FAIL", InspectionJudgeEngine.judgeItem(item, null, new BigDecimal("0.9"), null, null, null).getJudgement());
        }
    }
}
