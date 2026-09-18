-- =====================================================================
-- V12: 阶段6 新品引入（NPI）演示数据
--   11001 平阴玫瑰花茶 —— 已走完全流程并上市（三档评审/双供应商评估/验厂/外检）
--   11002 三年陈陈皮丝 —— 标准评审中（高档/中档已采购会签，待老板批准）
--   11003 金丝皇菊     —— 寻源送样中（已选定低档标准，评估已提交待定點）
--   11004 宁夏枸杞     —— 验厂中（已定点，初审单已排期）
--   11005 阿胶固元糕   —— 外检中（验厂合格，样品已寄 CMA 机构）
-- 幂等：固定主键，整段以首行 NOT EXISTS 守卫。
-- =====================================================================

-- ---------- 项目 ----------
INSERT INTO qc_npi_project
(id, project_no, project_name, category_id, brand, background, meeting_at, attendees, initiator_id,
 target_listing_date, status, target_grade, selected_template_id, chosen_supplier_id, product_id,
 std_submitted_at, std_approved_at, supplier_fixed_at, audit_passed_at, ext_test_passed_at, listed_at,
 terminate_reason, tenant_id, lock_version, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 11001 AS id, 'NP2026081101' AS project_no, '平阴玫瑰花茶新品引入' AS project_name, 301 AS category_id,
           '芳蕾' AS brand, '新品会确定引入平阴头期玫瑰花茶，定位自营高端花茶，需制定高中低三档标准并完成合规准入' AS background,
           '2026-08-05 10:00:00' AS meeting_at, '赵主管（品控）、周采购、王总' AS attendees, 5 AS initiator_id,
           '2026-09-10' AS target_listing_date, 'LISTED' AS status, 'MID' AS target_grade, 5102 AS selected_template_id,
           1003 AS chosen_supplier_id, 2004 AS product_id,
           '2026-08-08 14:00:00' AS std_submitted_at, '2026-08-11 16:30:00' AS std_approved_at,
           '2026-08-16 18:00:00' AS supplier_fixed_at, '2026-08-20 17:00:00' AS audit_passed_at,
           '2026-09-02 10:00:00' AS ext_test_passed_at, '2026-09-08 10:00:00' AS listed_at,
           NULL AS terminate_reason, 1 AS tenant_id, 0 AS lock_version, 5 AS created_by, '2026-08-05 11:00:00' AS created_at,
           5 AS updated_by, '2026-09-08 10:00:00' AS updated_at, 0 AS deleted
    UNION ALL
    SELECT 11002, 'NP2026090201', '三年陈陈皮丝（企业定制）', 202, '仁德堂',
           '新品会拟引入三年陈陈皮丝，主打药典品质老陈皮，标准需明确水分、橙皮苷及仓储年份判定',
           '2026-09-01 14:00:00', '赵主管（品控）、周采购、王总', 5,
           '2026-11-01', 'STD_REVIEW', NULL, NULL, NULL, NULL,
           '2026-09-04 10:00:00', NULL, NULL, NULL, NULL, NULL,
           NULL, 1, 0, 5, '2026-09-01 15:00:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL
    SELECT 11003, 'NP2026090501', '金丝皇菊礼盒装', 302, '芳蕾',
           '菊花茶类目增长快，拟引入金丝皇菊礼盒，走性价比档位快速上市',
           '2026-09-03 10:30:00', '赵主管（品控）、周采购', 5,
           '2026-10-20', 'SOURCING', 'LOW', 5109, NULL, NULL,
           '2026-09-06 11:00:00', '2026-09-09 15:00:00', NULL, NULL, NULL, NULL,
           NULL, 1, 0, 5, '2026-09-03 11:30:00', 5, '2026-09-09 15:00:00', 0
    UNION ALL
    SELECT 11004, 'NP2026090801', '宁夏特优级枸杞罐装', 200, '红宝',
           '滋补类目核心单品，需按中档标准引入道地产地供应商，重点验厂产地加工与仓储防虫',
           '2026-09-05 09:30:00', '赵主管（品控）、周采购、王总', 5,
           '2026-10-30', 'AUDIT', 'MID', 5111, 1002, NULL,
           '2026-09-08 14:00:00', '2026-09-10 17:00:00', '2026-09-13 18:00:00', NULL, NULL, NULL,
           NULL, 1, 0, 5, '2026-09-05 10:30:00', 5, '2026-09-13 18:00:00', 0
    UNION ALL
    SELECT 11005, 'NP2026091001', '阿胶固元糕（原味盒装）', 400, '美味鲜',
           '即食滋补食品，供应商为受控状态，须经严格验厂整改与外检合格后方可放行',
           '2026-08-28 14:00:00', '赵主管（品控）、周采购、王总', 5,
           '2026-10-15', 'EXT_TEST', 'MID', 5114, 1004, NULL,
           '2026-08-31 10:00:00', '2026-09-02 16:00:00', '2026-09-05 17:30:00',
           '2026-09-09 18:00:00', NULL, NULL,
           NULL, 1, 0, 5, '2026-08-28 15:00:00', 5, '2026-09-12 09:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_npi_project WHERE id = 11001);

-- ---------- 三档标准模板（每个项目 H/M/L 各一套 v1） ----------
INSERT INTO qc_standard_template
(id, template_code, template_name, category_id, package_form, grade, npi_project_id, regulation_basis, market_benchmark,
 version, status, max_a_fail, max_b_fail, max_c_fail, concession_allowed, remark, published_by, published_at,
 tenant_id, lock_version, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 5101 AS id, 'TPL_NP2026081101_H' AS template_code, '平阴玫瑰花茶·高档标准（企业定制）' AS template_name,
           301 AS category_id, NULL AS package_form, 'HIGH' AS grade, 11001 AS npi_project_id,
           'GH/T 1091《代用茶》；GB 2762 污染物限量；GB 2763 农药残留限量' AS regulation_basis,
           '对标头部品牌 80g 罐装头期花茶，硫残/农残加严 50%' AS market_benchmark,
           1 AS version, 'PUBLISHED' AS status, 0 AS max_a_fail, 1 AS max_b_fail, 2 AS max_c_fail, 0 AS concession_allowed,
           '高档：硫残≤0.15g/kg，头期花蕾，农残按企业加严清单' AS remark, 1 AS published_by, '2026-08-11 16:00:00' AS published_at,
           1 AS tenant_id, 0 AS lock_version, 5 AS created_by, '2026-08-06 10:00:00' AS created_at, 5 AS updated_by, '2026-08-11 16:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 5102, 'TPL_NP2026081101_M', '平阴玫瑰花茶·中档标准（国标加严）', 301, NULL, 'MID', 11001,
           'GH/T 1091《代用茶》；GB 2762/GB 2763', '对标主流电商自营花茶，硫残按国标果干类量级加严',
           1, 'PUBLISHED', 0, 2, 3, 1, '中档：硫残≤0.30g/kg，常规花蕾，允许让步评审',
           1, '2026-08-11 16:05:00', 1, 0, 5, '2026-08-06 10:10:00', 5, '2026-08-11 16:05:00', 0
    UNION ALL SELECT 5103, 'TPL_NP2026081101_L', '平阴玫瑰花茶·低档标准（准入底线）', 301, NULL, 'LOW', 11001,
           'GH/T 1091《代用茶》；GB 2760', '对标市场流通入门级产品，卡国标准入底线',
           1, 'PUBLISHED', 0, 2, 3, 1, '低档：硫残≤0.35g/kg（国标底线）',
           1, '2026-08-11 16:10:00', 1, 0, 5, '2026-08-06 10:20:00', 5, '2026-08-11 16:10:00', 0
    UNION ALL SELECT 5104, 'TPL_NP2026090201_H', '三年陈陈皮丝·高档标准（企业定制）', 202, NULL, 'HIGH', 11002,
           '《中国药典》2025年版一部陈皮项；GB/T 18862 陈皮', '对标老字号十年/三年陈产品，水分与年份判定加严',
           1, 'REVIEWING', 0, 1, 2, 0, '高档：水分≤13%，要求三年以上陈化溯源',
           NULL, NULL, 1, 0, 5, '2026-09-02 10:00:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL SELECT 5105, 'TPL_NP2026090201_M', '三年陈陈皮丝·中档标准（国标加严）', 202, NULL, 'MID', 11002,
           '《中国药典》2025年版一部；GB/T 18862', '主流药房/电商三年陈产品',
           1, 'REVIEWING', 0, 2, 3, 1, '中档：水分≤15%',
           NULL, NULL, 1, 0, 5, '2026-09-02 10:10:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL SELECT 5106, 'TPL_NP2026090201_L', '三年陈陈皮丝·低档标准（准入底线）', 202, NULL, 'LOW', 11002,
           '《中国药典》2025年版一部', '市场散装陈皮底线',
           1, 'REVIEWING', 0, 2, 3, 1, '低档：水分≤18%（药典上限）',
           NULL, NULL, 1, 0, 5, '2026-09-02 10:20:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL SELECT 5107, 'TPL_NP2026090501_H', '金丝皇菊·高档标准', 302, NULL, 'HIGH', 11003,
           'GH/T 1091；GB 2762 铅限量；GB 2763', '礼盒装头部品牌，铅限量加严',
           1, 'PUBLISHED', 0, 1, 2, 0, '高档：铅≤2.0mg/kg',
           1, '2026-09-09 14:50:00', 1, 0, 5, '2026-09-04 10:00:00', 5, '2026-09-09 14:50:00', 0
    UNION ALL SELECT 5108, 'TPL_NP2026090501_M', '金丝皇菊·中档标准', 302, NULL, 'MID', 11003,
           'GH/T 1091；GB 2762', '电商自营盒装主流水平',
           1, 'PUBLISHED', 0, 2, 3, 1, '中档：铅≤3.0mg/kg',
           1, '2026-09-09 14:55:00', 1, 0, 5, '2026-09-04 10:10:00', 5, '2026-09-09 14:55:00', 0
    UNION ALL SELECT 5109, 'TPL_NP2026090501_L', '金丝皇菊·低档标准（准入底线）', 302, NULL, 'LOW', 11003,
           'GH/T 1091；GB 2762', '市场礼盒入门款',
           1, 'PUBLISHED', 0, 2, 3, 1, '低档：铅≤5.0mg/kg（代用茶国标限量）',
           1, '2026-09-09 15:00:00', 1, 0, 5, '2026-09-04 10:20:00', 5, '2026-09-09 15:00:00', 0
    UNION ALL SELECT 5110, 'TPL_NP2026090801_H', '宁夏枸杞·高档标准', 200, NULL, 'HIGH', 11004,
           'GB/T 18672 枸杞；《中国药典》枸杞子项；GB 2762', '对标宁夏特优级道地产区，硫残/农残加严',
           1, 'PUBLISHED', 0, 1, 2, 0, '高档：二氧化硫≤0.20g/kg，特优级粒数',
           1, '2026-09-10 16:50:00', 1, 0, 5, '2026-09-06 10:00:00', 5, '2026-09-10 16:50:00', 0
    UNION ALL SELECT 5111, 'TPL_NP2026090801_M', '宁夏枸杞·中档标准（国标加严）', 200, NULL, 'MID', 11004,
           'GB/T 18672；GB 2760', '电商自营罐装主流水平',
           1, 'PUBLISHED', 0, 2, 3, 1, '中档：二氧化硫≤0.30g/kg，甲级果',
           1, '2026-09-10 16:55:00', 1, 0, 5, '2026-09-06 10:10:00', 5, '2026-09-10 16:55:00', 0
    UNION ALL SELECT 5112, 'TPL_NP2026090801_L', '宁夏枸杞·低档标准（准入底线）', 200, NULL, 'LOW', 11004,
           'GB/T 18672', '市场流通准入水平',
           1, 'PUBLISHED', 0, 2, 3, 1, '低档：二氧化硫≤0.35g/kg',
           1, '2026-09-10 17:00:00', 1, 0, 5, '2026-09-06 10:20:00', 5, '2026-09-10 17:00:00', 0
    UNION ALL SELECT 5113, 'TPL_NP2026091001_H', '阿胶固元糕·高档标准', 400, NULL, 'HIGH', 11005,
           'SB/T 10019；GB 7099 糕点卫生标准；GB 2762', '对标高端即食阿胶品牌，酸价/微生物加严',
           1, 'PUBLISHED', 0, 1, 2, 0, '高档：酸价≤2.0mg/g，菌落总数≤500CFU/g',
           1, '2026-09-02 15:50:00', 1, 0, 5, '2026-08-29 10:00:00', 5, '2026-09-02 15:50:00', 0
    UNION ALL SELECT 5114, 'TPL_NP2026091001_M', '阿胶固元糕·中档标准（国标加严）', 400, NULL, 'MID', 11005,
           'SB/T 10019；GB 7099', '电商主流即食阿胶糕',
           1, 'PUBLISHED', 0, 2, 3, 1, '中档：酸价≤3.0mg/g',
           1, '2026-09-02 15:55:00', 1, 0, 5, '2026-08-29 10:10:00', 5, '2026-09-02 15:55:00', 0
    UNION ALL SELECT 5115, 'TPL_NP2026091001_L', '阿胶固元糕·低档标准（准入底线）', 400, NULL, 'LOW', 11005,
           'SB/T 10019；GB 7099', '市场准入水平',
           1, 'PUBLISHED', 0, 2, 3, 1, '低档：酸价≤4.0mg/g',
           1, '2026-09-02 16:00:00', 1, 0, 5, '2026-08-29 10:20:00', 5, '2026-09-02 16:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_standard_template WHERE id = 5101);

-- ---------- 档位标准检验项（每套 3 项：感官/关键理化/资质外检） ----------
INSERT INTO qc_standard_item
(id, template_id, group_code, item_name, inspect_method, result_type, defect_level, veto_flag, required_flag,
 min_value, max_value, nominal_value, unit, tolerance_rule, judge_config, sort,
 tenant_id, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 6401 AS id, 5101 AS template_id, 'SENSORY' AS group_code, '头期花蕾完整，紫红色均匀，无碎花碎末>5%' AS item_name,
           '目检' AS inspect_method, 'QUALITATIVE' AS result_type, 'A' AS defect_level, 1 AS veto_flag, 1 AS required_flag,
           NULL AS min_value, NULL AS max_value, NULL AS nominal_value, NULL AS unit, NULL AS tolerance_rule, NULL AS judge_config, 10 AS sort,
           1 AS tenant_id, 5 AS created_by, '2026-08-06 10:00:00' AS created_at, 5 AS updated_by, '2026-08-06 10:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 6402, 5101, 'PHYSICO', '二氧化硫残留量（加严）', '滴定法', 'QUANTITATIVE', 'A', 1, 1, NULL, 0.1500, NULL, 'g/kg', NULL, NULL, 20, 1, 5, '2026-08-06 10:00:00', 5, '2026-08-06 10:00:00', 0
    UNION ALL SELECT 6403, 5101, 'CERT_DOC', '第三方 CMA/CNAS 外检报告（同批号结论合格）', '资料核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-08-06 10:00:00', 5, '2026-08-06 10:00:00', 0
    UNION ALL SELECT 6404, 5102, 'SENSORY', '花朵完整、色泽紫红均匀，无霉变虫蛀', '目检', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-08-06 10:10:00', 5, '2026-08-06 10:10:00', 0
    UNION ALL SELECT 6405, 5102, 'PHYSICO', '二氧化硫残留量', '滴定法', 'QUANTITATIVE', 'A', 0, 1, NULL, 0.3000, NULL, 'g/kg', NULL, NULL, 20, 1, 5, '2026-08-06 10:10:00', 5, '2026-08-06 10:10:00', 0
    UNION ALL SELECT 6406, 5102, 'CERT_DOC', '出厂检验报告（同批号）', '资料核验', 'DOCUMENT', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-08-06 10:10:00', 5, '2026-08-06 10:10:00', 0
    UNION ALL SELECT 6407, 5103, 'SENSORY', '干花色泽正常，无霉变、异味、杂质', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-08-06 10:20:00', 5, '2026-08-06 10:20:00', 0
    UNION ALL SELECT 6408, 5103, 'PHYSICO', '二氧化硫残留量（国标底线）', '滴定法', 'QUANTITATIVE', 'A', 0, 1, NULL, 0.3500, NULL, 'g/kg', NULL, NULL, 20, 1, 5, '2026-08-06 10:20:00', 5, '2026-08-06 10:20:00', 0
    UNION ALL SELECT 6409, 5103, 'CERT_DOC', 'SC 生产许可在有效期内', '证照核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-08-06 10:20:00', 5, '2026-08-06 10:20:00', 0
    UNION ALL SELECT 6410, 5104, 'SENSORY', '丝条厚薄均匀，外表面棕红色、内表面浅黄，气香辛苦', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-02 10:00:00', 5, '2026-09-02 10:00:00', 0
    UNION ALL SELECT 6411, 5104, 'PHYSICO', '水分（加严，三年陈）', '烘干法', 'QUANTITATIVE', 'B', 0, 1, NULL, 13.0000, NULL, '%', NULL, NULL, 20, 1, 5, '2026-09-02 10:00:00', 5, '2026-09-02 10:00:00', 0
    UNION ALL SELECT 6412, 5104, 'CERT_DOC', '三年陈化溯源记录（入库/转仓台账）', '资料核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-02 10:00:00', 5, '2026-09-02 10:00:00', 0
    UNION ALL SELECT 6413, 5105, 'SENSORY', '丝条均匀，色泽气味符合药典陈皮项', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-02 10:10:00', 5, '2026-09-02 10:10:00', 0
    UNION ALL SELECT 6414, 5105, 'PHYSICO', '水分', '烘干法', 'QUANTITATIVE', 'B', 0, 1, NULL, 15.0000, NULL, '%', NULL, NULL, 20, 1, 5, '2026-09-02 10:10:00', 5, '2026-09-02 10:10:00', 0
    UNION ALL SELECT 6415, 5105, 'CERT_DOC', '出厂检验报告（同批号）', '资料核验', 'DOCUMENT', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-02 10:10:00', 5, '2026-09-02 10:10:00', 0
    UNION ALL SELECT 6416, 5106, 'SENSORY', '无霉变、虫蛀、杂质，气味正常', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-02 10:20:00', 5, '2026-09-02 10:20:00', 0
    UNION ALL SELECT 6417, 5106, 'PHYSICO', '水分（药典上限）', '烘干法', 'QUANTITATIVE', 'B', 0, 1, NULL, 18.0000, NULL, '%', NULL, NULL, 20, 1, 5, '2026-09-02 10:20:00', 5, '2026-09-02 10:20:00', 0
    UNION ALL SELECT 6418, 5106, 'CERT_DOC', '供应商经营/生产资质在有效期内', '证照核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-02 10:20:00', 5, '2026-09-02 10:20:00', 0
    UNION ALL SELECT 6419, 5107, 'SENSORY', '花朵完整硕大、金黄均匀，包装无破损', '目检', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-04 10:00:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL SELECT 6420, 5107, 'PHYSICO', '铅（Pb，加严）', 'ICP-MS', 'QUANTITATIVE', 'A', 1, 1, NULL, 2.0000, NULL, 'mg/kg', NULL, NULL, 20, 1, 5, '2026-09-04 10:00:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL SELECT 6421, 5107, 'CERT_DOC', 'CMA/CNAS 外检报告', '资料核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-04 10:00:00', 5, '2026-09-04 10:00:00', 0
    UNION ALL SELECT 6422, 5108, 'SENSORY', '花朵完整、色泽金黄，无霉变杂质', '目检', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-04 10:10:00', 5, '2026-09-04 10:10:00', 0
    UNION ALL SELECT 6423, 5108, 'PHYSICO', '铅（Pb）', 'ICP-MS', 'QUANTITATIVE', 'A', 0, 1, NULL, 3.0000, NULL, 'mg/kg', NULL, NULL, 20, 1, 5, '2026-09-04 10:10:00', 5, '2026-09-04 10:10:00', 0
    UNION ALL SELECT 6424, 5108, 'CERT_DOC', '出厂检验报告（同批号）', '资料核验', 'DOCUMENT', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-04 10:10:00', 5, '2026-09-04 10:10:00', 0
    UNION ALL SELECT 6425, 5109, 'SENSORY', '干花色泽正常，无霉变、虫蛀、异味', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-04 10:20:00', 5, '2026-09-04 10:20:00', 0
    UNION ALL SELECT 6426, 5109, 'PHYSICO', '铅（Pb，国标限量）', 'ICP-MS', 'QUANTITATIVE', 'A', 0, 1, NULL, 5.0000, NULL, 'mg/kg', NULL, NULL, 20, 1, 5, '2026-09-04 10:20:00', 5, '2026-09-04 10:20:00', 0
    UNION ALL SELECT 6427, 5109, 'CERT_DOC', 'SC 生产许可在有效期内', '证照核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-04 10:20:00', 5, '2026-09-04 10:20:00', 0
    UNION ALL SELECT 6428, 5110, 'SENSORY', '特优级粒数/杂质≤0.5%，颗粒饱满暗红', '目检/计数', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-06 10:00:00', 5, '2026-09-06 10:00:00', 0
    UNION ALL SELECT 6429, 5110, 'PHYSICO', '二氧化硫残留量（加严）', '滴定法', 'QUANTITATIVE', 'A', 1, 1, NULL, 0.2000, NULL, 'g/kg', NULL, NULL, 20, 1, 5, '2026-09-06 10:00:00', 5, '2026-09-06 10:00:00', 0
    UNION ALL SELECT 6430, 5110, 'CERT_DOC', '产地证明+第三方外检报告', '资料核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-06 10:00:00', 5, '2026-09-06 10:00:00', 0
    UNION ALL SELECT 6431, 5111, 'SENSORY', '甲级果，颗粒饱满，无结块霉变', '目检', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-06 10:10:00', 5, '2026-09-06 10:10:00', 0
    UNION ALL SELECT 6432, 5111, 'PHYSICO', '二氧化硫残留量', '滴定法', 'QUANTITATIVE', 'A', 0, 1, NULL, 0.3000, NULL, 'g/kg', NULL, NULL, 20, 1, 5, '2026-09-06 10:10:00', 5, '2026-09-06 10:10:00', 0
    UNION ALL SELECT 6433, 5111, 'CERT_DOC', '出厂检验报告（同批号）', '资料核验', 'DOCUMENT', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-06 10:10:00', 5, '2026-09-06 10:10:00', 0
    UNION ALL SELECT 6434, 5112, 'SENSORY', '颗粒色泽正常，无霉变、虫蛀、结块', '目检', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-09-06 10:20:00', 5, '2026-09-06 10:20:00', 0
    UNION ALL SELECT 6435, 5112, 'PHYSICO', '二氧化硫残留量（准入底线）', '滴定法', 'QUANTITATIVE', 'A', 0, 1, NULL, 0.3500, NULL, 'g/kg', NULL, NULL, 20, 1, 5, '2026-09-06 10:20:00', 5, '2026-09-06 10:20:00', 0
    UNION ALL SELECT 6436, 5112, 'CERT_DOC', '食品经营资质在有效期内', '证照核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-09-06 10:20:00', 5, '2026-09-06 10:20:00', 0
    UNION ALL SELECT 6437, 5113, 'SENSORY', '切面乌黑油亮有光泽，具阿胶特有香气，无哈喇味', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-08-29 10:00:00', 5, '2026-08-29 10:00:00', 0
    UNION ALL SELECT 6438, 5113, 'PHYSICO', '酸价（以脂肪计，加严）', '滴定法', 'QUANTITATIVE', 'A', 1, 1, NULL, 2.0000, NULL, 'mg/g', NULL, NULL, 20, 1, 5, '2026-08-29 10:00:00', 5, '2026-08-29 10:00:00', 0
    UNION ALL SELECT 6439, 5113, 'CERT_DOC', 'CMA 外检报告（微生物+理化全项）', '资料核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-08-29 10:00:00', 5, '2026-08-29 10:00:00', 0
    UNION ALL SELECT 6440, 5114, 'SENSORY', '块形完整、色泽均匀，无异味、霉变、胀袋', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-08-29 10:10:00', 5, '2026-08-29 10:10:00', 0
    UNION ALL SELECT 6441, 5114, 'PHYSICO', '酸价（以脂肪计）', '滴定法', 'QUANTITATIVE', 'B', 0, 1, NULL, 3.0000, NULL, 'mg/g', NULL, NULL, 20, 1, 5, '2026-08-29 10:10:00', 5, '2026-08-29 10:10:00', 0
    UNION ALL SELECT 6442, 5114, 'CERT_DOC', '出厂检验报告（同批号）', '资料核验', 'DOCUMENT', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-08-29 10:10:00', 5, '2026-08-29 10:10:00', 0
    UNION ALL SELECT 6443, 5115, 'SENSORY', '无酸败、霉变、杂质，包装完整', '目检/鼻嗅', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10, 1, 5, '2026-08-29 10:20:00', 5, '2026-08-29 10:20:00', 0
    UNION ALL SELECT 6444, 5115, 'PHYSICO', '酸价（以脂肪计，准入）', '滴定法', 'QUANTITATIVE', 'B', 0, 1, NULL, 4.0000, NULL, 'mg/g', NULL, NULL, 20, 1, 5, '2026-08-29 10:20:00', 5, '2026-08-29 10:20:00', 0
    UNION ALL SELECT 6445, 5115, 'CERT_DOC', 'SC 生产许可在有效期内', '证照核验', 'DOCUMENT', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30, 1, 5, '2026-08-29 10:20:00', 5, '2026-08-29 10:20:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_standard_item WHERE id = 6401);

-- ---------- 标准评审记录（提交评审→采购会签→老板批准） ----------
INSERT INTO qc_std_review (id, template_id, node, action, comment, operator_id, operator_name, operated_at, tenant_id)
SELECT * FROM (
    SELECT 15001 AS id, 5101 AS template_id, 'SUBMIT' AS node, 'SUBMIT' AS action, '高档标准起草完成，国标依据与竞品对标已填写，提请采购/老板评审' AS comment, 5 AS operator_id, '赵主管' AS operator_name, '2026-08-08 14:00:00' AS operated_at, 1 AS tenant_id
    UNION ALL SELECT 15002, 5101, 'PROCUREMENT', 'PASS', '采购会签：加严指标可支撑高端定价，同意', 1, '系统管理员', '2026-08-09 10:30:00', 1
    UNION ALL SELECT 15003, 5101, 'BOSS', 'PASS', '老板批准：按高档执行，外检合格后上市', 1, '系统管理员', '2026-08-11 16:00:00', 1
    UNION ALL SELECT 15004, 5102, 'SUBMIT', 'SUBMIT', '中档标准起草完成，建议作为寻源主档位', 5, '赵主管', '2026-08-08 14:05:00', 1
    UNION ALL SELECT 15005, 5102, 'PROCUREMENT', 'PASS', '采购会签：中档成本可控、供应充足，建议选此档寻源', 1, '系统管理员', '2026-08-09 10:35:00', 1
    UNION ALL SELECT 15006, 5102, 'BOSS', 'PASS', '老板批准：选中档寻源', 1, '系统管理员', '2026-08-11 16:05:00', 1
    UNION ALL SELECT 15007, 5103, 'SUBMIT', 'SUBMIT', '低档准入标准起草完成', 5, '赵主管', '2026-08-08 14:10:00', 1
    UNION ALL SELECT 15008, 5103, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-08-09 10:40:00', 1
    UNION ALL SELECT 15009, 5103, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-08-11 16:10:00', 1
    UNION ALL SELECT 15010, 5104, 'SUBMIT', 'SUBMIT', '高档标准（水分13%+三年陈溯源）提请评审', 5, '赵主管', '2026-09-04 10:00:00', 1
    UNION ALL SELECT 15011, 5104, 'PROCUREMENT', 'PASS', '采购会签：定制款可接受，待老板最终批准', 1, '系统管理员', '2026-09-07 11:00:00', 1
    UNION ALL SELECT 15012, 5105, 'SUBMIT', 'SUBMIT', '中档标准提请评审', 5, '赵主管', '2026-09-04 10:05:00', 1
    UNION ALL SELECT 15013, 5105, 'PROCUREMENT', 'PASS', '采购会签：同意中档', 1, '系统管理员', '2026-09-07 11:05:00', 1
    UNION ALL SELECT 15014, 5106, 'SUBMIT', 'SUBMIT', '低档标准提请评审，待采购会签', 5, '赵主管', '2026-09-04 10:10:00', 1
    UNION ALL SELECT 15015, 5107, 'SUBMIT', 'SUBMIT', '高档标准提请评审', 5, '赵主管', '2026-09-06 11:00:00', 1
    UNION ALL SELECT 15016, 5107, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-09-08 09:30:00', 1
    UNION ALL SELECT 15017, 5107, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-09-09 14:50:00', 1
    UNION ALL SELECT 15018, 5108, 'SUBMIT', 'SUBMIT', '中档标准提请评审', 5, '赵主管', '2026-09-06 11:05:00', 1
    UNION ALL SELECT 15019, 5108, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-09-08 09:35:00', 1
    UNION ALL SELECT 15020, 5108, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-09-09 14:55:00', 1
    UNION ALL SELECT 15021, 5109, 'SUBMIT', 'SUBMIT', '低档准入标准提请评审', 5, '赵主管', '2026-09-06 11:10:00', 1
    UNION ALL SELECT 15022, 5109, 'PROCUREMENT', 'PASS', '采购会签：礼盒款按低档寻源，控价优先', 1, '系统管理员', '2026-09-08 09:40:00', 1
    UNION ALL SELECT 15023, 5109, 'BOSS', 'PASS', '老板批准：选低档寻源', 1, '系统管理员', '2026-09-09 15:00:00', 1
    UNION ALL SELECT 15024, 5110, 'SUBMIT', 'SUBMIT', '高档标准提请评审', 5, '赵主管', '2026-09-08 14:00:00', 1
    UNION ALL SELECT 15025, 5110, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-09-09 15:00:00', 1
    UNION ALL SELECT 15026, 5110, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-09-10 16:50:00', 1
    UNION ALL SELECT 15027, 5111, 'SUBMIT', 'SUBMIT', '中档标准提请评审，建议选此档', 5, '赵主管', '2026-09-08 14:05:00', 1
    UNION ALL SELECT 15028, 5111, 'PROCUREMENT', 'PASS', '采购会签：同意中档，成本品质平衡', 1, '系统管理员', '2026-09-09 15:05:00', 1
    UNION ALL SELECT 15029, 5111, 'BOSS', 'PASS', '老板批准：选中档寻源', 1, '系统管理员', '2026-09-10 16:55:00', 1
    UNION ALL SELECT 15030, 5112, 'SUBMIT', 'SUBMIT', '低档标准提请评审', 5, '赵主管', '2026-09-08 14:10:00', 1
    UNION ALL SELECT 15031, 5112, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-09-09 15:10:00', 1
    UNION ALL SELECT 15032, 5112, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-09-10 17:00:00', 1
    UNION ALL SELECT 15033, 5113, 'SUBMIT', 'SUBMIT', '高档标准提请评审', 5, '赵主管', '2026-08-31 10:00:00', 1
    UNION ALL SELECT 15034, 5113, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-09-01 10:00:00', 1
    UNION ALL SELECT 15035, 5113, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-09-02 15:50:00', 1
    UNION ALL SELECT 15036, 5114, 'SUBMIT', 'SUBMIT', '中档标准提请评审，供应商受控须加严', 5, '赵主管', '2026-08-31 10:05:00', 1
    UNION ALL SELECT 15037, 5114, 'PROCUREMENT', 'PASS', '采购会签：同意中档', 1, '系统管理员', '2026-09-01 10:05:00', 1
    UNION ALL SELECT 15038, 5114, 'BOSS', 'PASS', '老板批准：选中档，验厂不过立即换供应商', 1, '系统管理员', '2026-09-02 15:55:00', 1
    UNION ALL SELECT 15039, 5115, 'SUBMIT', 'SUBMIT', '低档标准提请评审', 5, '赵主管', '2026-08-31 10:10:00', 1
    UNION ALL SELECT 15040, 5115, 'PROCUREMENT', 'PASS', '采购会签：同意', 1, '系统管理员', '2026-09-01 10:10:00', 1
    UNION ALL SELECT 15041, 5115, 'BOSS', 'PASS', '老板批准', 1, '系统管理员', '2026-09-02 16:00:00', 1
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_std_review WHERE id = 15001);

-- ---------- 送样评估单 ----------
INSERT INTO qc_npi_eval
(id, eval_no, project_id, supplier_id, round_no, sample_desc, received_at, quality_conclusion,
 total_score, rank_no, selected_flag, remark, status, tenant_id, lock_version,
 created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 12001 AS id, 'PG2026081401' AS eval_no, 11001 AS project_id, 1003 AS supplier_id, 1 AS round_no,
           '头期玫瑰花茶 50g/罐 × 12 罐，批号 FL20260801' AS sample_desc, '2026-08-14 10:00:00' AS received_at,
           'QUALIFIED' AS quality_conclusion, 88.40 AS total_score, 1 AS rank_no, 1 AS selected_flag,
           '对照中档标准全项符合，感官与交期表现突出' AS remark, 'SUBMITTED' AS status, 1 AS tenant_id, 0 AS lock_version,
           5 AS created_by, '2026-08-14 11:00:00' AS created_at, 5 AS updated_by, '2026-08-15 16:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 12002, 'PG2026081402', 11001, 1004, 1, '玫瑰花茶 50g/罐 × 12 罐，批号 MW20260806', '2026-08-14 15:00:00',
           'QUALIFIED', 80.20, 2, 0, '符合中档标准，但包装与交期评分偏低', 'SUBMITTED', 1, 0,
           5, '2026-08-14 16:00:00', 5, '2026-08-15 16:30:00', 0
    UNION ALL SELECT 12003, 'PG2026091201', 11003, 1003, 1, '金丝皇菊 20朵/盒 × 20 盒，批号 FL20260905', '2026-09-12 09:30:00',
           'QUALIFIED', 85.00, 1, 0, '对照低档标准符合，待评审定点', 'SUBMITTED', 1, 0,
           5, '2026-09-12 10:00:00', 5, '2026-09-13 11:00:00', 0
    UNION ALL SELECT 12004, 'PG2026091202', 11004, 1002, 1, '特优级枸杞 250g/罐 × 12 罐，批号 HB20260901', '2026-09-12 14:00:00',
           'QUALIFIED', 89.00, 1, 1, '对照中档标准全项符合，道地产地溯源资料齐全', 'SUBMITTED', 1, 0,
           5, '2026-09-12 15:00:00', 5, '2026-09-13 17:00:00', 0
    UNION ALL SELECT 12005, 'PG2026090601', 11005, 1004, 1, '阿胶固元糕 15g×15片/盒 × 10 盒，批号 MW20260901', '2026-09-04 10:00:00',
           'QUALIFIED', 82.00, 1, 1, '对照中档标准符合，供应商受控，以验厂/外检结论为准', 'SUBMITTED', 1, 0,
           5, '2026-09-04 11:00:00', 5, '2026-09-05 16:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_npi_eval WHERE id = 12001);

-- ---------- 评估五维度评分（权重各20，总分=均值） ----------
INSERT INTO qc_npi_eval_item
(id, eval_id, dimension_code, dimension_name, score, weight, note, tenant_id, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 12101 AS id, 12001 AS eval_id, 'QUALITY' AS dimension_code, '质量符合性' AS dimension_name, 90.00 AS score, 20.00 AS weight, '硫残0.18g/kg、水分8.2%，符合中档' AS note, 1 AS tenant_id, 5 AS created_by, '2026-08-15 15:00:00' AS created_at, 5 AS updated_by, '2026-08-15 15:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 12102, 12001, 'PACKAGE', '包装与标签', 88.00, 20.00, '罐装密封好，标签合规，礼盒设计待优化', 1, 5, '2026-08-15 15:00:00', 5, '2026-08-15 15:00:00', 0
    UNION ALL SELECT 12103, 12001, 'PRICE', '价格竞争力', 82.00, 20.00, '报价略高于行业均值，但品质匹配', 1, 5, '2026-08-15 15:00:00', 5, '2026-08-15 15:00:00', 0
    UNION ALL SELECT 12104, 12001, 'DELIVERY', '交期保障', 92.00, 20.00, '7 天内交货，产能充足', 1, 5, '2026-08-15 15:00:00', 5, '2026-08-15 15:00:00', 0
    UNION ALL SELECT 12105, 12001, 'SERVICE', '服务配合', 90.00, 20.00, '配合补寄样品与资料，响应及时', 1, 5, '2026-08-15 15:00:00', 5, '2026-08-15 15:00:00', 0
    UNION ALL SELECT 12106, 12002, 'QUALITY', '质量符合性', 82.00, 20.00, '硫残0.27g/kg 符合中档，碎瓣偏多', 1, 5, '2026-08-15 16:00:00', 5, '2026-08-15 16:00:00', 0
    UNION ALL SELECT 12107, 12002, 'PACKAGE', '包装与标签', 80.00, 20.00, '外箱有轻微压痕', 1, 5, '2026-08-15 16:00:00', 5, '2026-08-15 16:00:00', 0
    UNION ALL SELECT 12108, 12002, 'PRICE', '价格竞争力', 84.00, 20.00, '报价较低', 1, 5, '2026-08-15 16:00:00', 5, '2026-08-15 16:00:00', 0
    UNION ALL SELECT 12109, 12002, 'DELIVERY', '交期保障', 78.00, 20.00, '交期 15 天，旺季存在不确定性', 1, 5, '2026-08-15 16:00:00', 5, '2026-08-15 16:00:00', 0
    UNION ALL SELECT 12110, 12002, 'SERVICE', '服务配合', 77.00, 20.00, '资料提供较慢', 1, 5, '2026-08-15 16:00:00', 5, '2026-08-15 16:00:00', 0
    UNION ALL SELECT 12111, 12003, 'QUALITY', '质量符合性', 86.00, 20.00, '铅2.8mg/kg，符合低档', 1, 5, '2026-09-13 10:00:00', 5, '2026-09-13 10:00:00', 0
    UNION ALL SELECT 12112, 12003, 'PACKAGE', '包装与标签', 84.00, 20.00, '礼盒包装完好', 1, 5, '2026-09-13 10:00:00', 5, '2026-09-13 10:00:00', 0
    UNION ALL SELECT 12113, 12003, 'PRICE', '价格竞争力', 82.00, 20.00, '价格满足礼盒控价', 1, 5, '2026-09-13 10:00:00', 5, '2026-09-13 10:00:00', 0
    UNION ALL SELECT 12114, 12003, 'DELIVERY', '交期保障', 88.00, 20.00, '现货充足', 1, 5, '2026-09-13 10:00:00', 5, '2026-09-13 10:00:00', 0
    UNION ALL SELECT 12115, 12003, 'SERVICE', '服务配合', 85.00, 20.00, '配合度好', 1, 5, '2026-09-13 10:00:00', 5, '2026-09-13 10:00:00', 0
    UNION ALL SELECT 12116, 12004, 'QUALITY', '质量符合性', 90.00, 20.00, '硫残0.21g/kg，特优级粒数达标', 1, 5, '2026-09-13 16:00:00', 5, '2026-09-13 16:00:00', 0
    UNION ALL SELECT 12117, 12004, 'PACKAGE', '包装与标签', 88.00, 20.00, '罐装防潮性能好', 1, 5, '2026-09-13 16:00:00', 5, '2026-09-13 16:00:00', 0
    UNION ALL SELECT 12118, 12004, 'PRICE', '价格竞争力', 86.00, 20.00, '道地产地价格适中', 1, 5, '2026-09-13 16:00:00', 5, '2026-09-13 16:00:00', 0
    UNION ALL SELECT 12119, 12004, 'DELIVERY', '交期保障', 91.00, 20.00, '产地直发，10 天内交货', 1, 5, '2026-09-13 16:00:00', 5, '2026-09-13 16:00:00', 0
    UNION ALL SELECT 12120, 12004, 'SERVICE', '服务配合', 90.00, 20.00, '合作社配合验厂资料', 1, 5, '2026-09-13 16:00:00', 5, '2026-09-13 16:00:00', 0
    UNION ALL SELECT 12121, 12005, 'QUALITY', '质量符合性', 82.00, 20.00, '酸价2.6mg/g 符合中档，待外检确认微生物', 1, 5, '2026-09-05 15:00:00', 5, '2026-09-05 15:00:00', 0
    UNION ALL SELECT 12122, 12005, 'PACKAGE', '包装与标签', 84.00, 20.00, '独立小包装完整', 1, 5, '2026-09-05 15:00:00', 5, '2026-09-05 15:00:00', 0
    UNION ALL SELECT 12123, 12005, 'PRICE', '价格竞争力', 80.00, 20.00, '价格偏高，待谈判', 1, 5, '2026-09-05 15:00:00', 5, '2026-09-05 15:00:00', 0
    UNION ALL SELECT 12124, 12005, 'DELIVERY', '交期保障', 83.00, 20.00, '12 天交期', 1, 5, '2026-09-05 15:00:00', 5, '2026-09-05 15:00:00', 0
    UNION ALL SELECT 12125, 12005, 'SERVICE', '服务配合', 81.00, 20.00, '愿意按整改清单投入改造', 1, 5, '2026-09-05 15:00:00', 5, '2026-09-05 15:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_npi_eval_item WHERE id = 12101);

-- ---------- 验厂单 ----------
INSERT INTO qc_factory_audit
(id, audit_no, project_id, supplier_id, parent_audit_id, audit_type, planned_at, audited_at, leader_id, auditors,
 total_score, conclusion, rectify_requirement, rectify_deadline, report_attachment_id, status, remark,
 tenant_id, lock_version, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 13001 AS id, 'YC2026081901' AS audit_no, 11001 AS project_id, 1003 AS supplier_id, NULL AS parent_audit_id,
           'INITIAL' AS audit_type, '2026-08-19 09:00:00' AS planned_at, '2026-08-19 16:00:00' AS audited_at,
           5 AS leader_id, '赵主管、李检验、周采购' AS auditors, 89.50 AS total_score, 'PASS' AS conclusion,
           NULL AS rectify_requirement, NULL AS rectify_deadline, NULL AS report_attachment_id, 'CONFIRMED' AS status,
           '生产/仓储/质量体系均符合，冷链不适用已备案' AS remark, 1 AS tenant_id, 0 AS lock_version,
           5 AS created_by, '2026-08-17 10:00:00' AS created_at, 5 AS updated_by, '2026-08-20 17:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 13002, 'YC2026091701', 11004, 1002, NULL, 'INITIAL', '2026-09-17 09:00:00', NULL,
           5, '赵主管、李检验', NULL, NULL, NULL, NULL, NULL, 'PLANNED',
           '计划赴宁夏中宁现场审核产地加工、仓储防虫与效期管理', 1, 0,
           5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13003, 'YC2026090801', 11005, 1004, NULL, 'INITIAL', '2026-09-08 09:00:00', '2026-09-08 17:00:00',
           5, '赵主管、王复核', 86.00, 'PASS',
           '冷藏库温湿度记录频率提高到每2小时一次，成品库增设挡鼠板', '2026-09-25', NULL, 'CONFIRMED',
           '受控供应商整改后复审现场，已确认整改到位，结论合格', 1, 0,
           5, '2026-09-06 10:00:00', 5, '2026-09-09 18:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_factory_audit WHERE id = 13001);

-- ---------- 验厂检查项 ----------
INSERT INTO qc_factory_audit_item
(id, audit_id, category, item_name, requirement, max_score, score, result, note, sort,
 tenant_id, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 13101 AS id, 13001 AS audit_id, 'QUALIFICATION' AS category, '资质证照合规' AS item_name,
           '营业执照/SC许可/经营范围在有效期内，覆盖代用茶' AS requirement, 100.00 AS max_score, 92.00 AS score,
           'COMPLIANT' AS result, 'SC11433018500300 有效（注：经营许可临期已提醒续期）' AS note, 1 AS sort,
           1 AS tenant_id, 5 AS created_by, '2026-08-19 16:00:00' AS created_at, 5 AS updated_by, '2026-08-19 16:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 13102, 13001, 'PRODUCTION', '生产车间与设备合规', '洁净作业区、设备校准、虫害控制', 100.00, 90.00, 'COMPLIANT', '花茶拣选/烘干车间分区清晰', 2, 1, 5, '2026-08-19 16:00:00', 5, '2026-08-19 16:00:00', 0
    UNION ALL SELECT 13103, 13001, 'PRODUCTION', '生产过程质量控制', '批记录/出厂检验/批号追溯', 100.00, 85.00, 'COMPLIANT', '每批留样，出厂全检记录完整', 3, 1, 5, '2026-08-19 16:00:00', 5, '2026-08-19 16:00:00', 0
    UNION ALL SELECT 13104, 13001, 'WAREHOUSE', '仓储条件合规', '分区/离地离墙/效期与先进先出', 100.00, 90.00, 'COMPLIANT', '原辅料/成品分库，阴凉库温湿度记录完整', 4, 1, 5, '2026-08-19 16:00:00', 5, '2026-08-19 16:00:00', 0
    UNION ALL SELECT 13105, 13001, 'COLD_CHAIN', '冷链保障能力', '冷藏库监测/冷链运输（如适用）', 100.00, 88.00, 'NA', '常温花茶不适用，阴凉库能力已核', 5, 1, 5, '2026-08-19 16:00:00', 5, '2026-08-19 16:00:00', 0
    UNION ALL SELECT 13106, 13001, 'QUALITY_SYSTEM', '质量体系与人员', '制度/不合格品控制/人员培训', 100.00, 92.00, 'COMPLIANT', '专职质检2人，培训档案齐全', 6, 1, 5, '2026-08-19 16:00:00', 5, '2026-08-19 16:00:00', 0
    UNION ALL SELECT 13107, 13002, 'QUALIFICATION', '资质证照合规', '营业执照/食品经营许可覆盖枸杞', 100.00, NULL, NULL, NULL, 1, 1, 5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13108, 13002, 'PRODUCTION', '生产车间与设备合规', '产地加工场地/拣选/烘干设备', 100.00, NULL, NULL, NULL, 2, 1, 5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13109, 13002, 'PRODUCTION', '生产过程质量控制', '批记录/出厂检验/追溯', 100.00, NULL, NULL, NULL, 3, 1, 5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13110, 13002, 'WAREHOUSE', '仓储条件合规', '防虫鼠/防潮/效期管理', 100.00, NULL, NULL, NULL, 4, 1, 5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13111, 13002, 'COLD_CHAIN', '冷链保障能力', '枸杞阴凉储存，夏季温控', 100.00, NULL, NULL, NULL, 5, 1, 5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13112, 13002, 'QUALITY_SYSTEM', '质量体系与人员', '制度/人员配置', 100.00, NULL, NULL, NULL, 6, 1, 5, '2026-09-14 10:00:00', 5, '2026-09-14 10:00:00', 0
    UNION ALL SELECT 13113, 13003, 'QUALIFICATION', '资质证照合规', 'SC许可覆盖糕点（热加工）', 100.00, 88.00, 'COMPLIANT', 'SC10644200000400 有效', 1, 1, 5, '2026-09-08 17:00:00', 5, '2026-09-08 17:00:00', 0
    UNION ALL SELECT 13114, 13003, 'PRODUCTION', '生产车间与设备合规', '蒸煮/切制/内包车间卫生', 100.00, 85.00, 'COMPLIANT', '整改后更衣消毒流程已规范', 2, 1, 5, '2026-09-08 17:00:00', 5, '2026-09-08 17:00:00', 0
    UNION ALL SELECT 13115, 13003, 'WAREHOUSE', '仓储条件合规', '原料阿胶/黄酒专库，成品阴凉库', 100.00, 82.00, 'COMPLIANT', '挡鼠板已增设', 3, 1, 5, '2026-09-08 17:00:00', 5, '2026-09-08 17:00:00', 0
    UNION ALL SELECT 13116, 13003, 'COLD_CHAIN', '冷链保障能力', '夏季冷藏库与冷链运输温控', 100.00, 84.00, 'COMPLIANT', '温湿度记录已提频至2小时一次', 4, 1, 5, '2026-09-08 17:00:00', 5, '2026-09-08 17:00:00', 0
    UNION ALL SELECT 13117, 13003, 'QUALITY_SYSTEM', '质量体系与人员', '出厂微生物检验能力/留样', 100.00, 86.00, 'COMPLIANT', '新增微生物快检设备', 5, 1, 5, '2026-09-08 17:00:00', 5, '2026-09-08 17:00:00', 0
    UNION ALL SELECT 13118, 13003, 'PRODUCTION', '生产过程质量控制', '批生产记录/金属探测/批号追溯', 100.00, 91.00, 'COMPLIANT', '金探记录完整', 6, 1, 5, '2026-09-08 17:00:00', 5, '2026-09-08 17:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_factory_audit_item WHERE id = 13101);

-- ---------- 外检送检单 ----------
INSERT INTO qc_external_test
(id, test_no, project_id, supplier_id, product_name, sample_desc, lab_name, lab_qualification, test_items,
 sent_at, report_no, report_date, conclusion, report_attachment_id, status, remark,
 tenant_id, lock_version, created_by, created_at, updated_by, updated_at, deleted)
SELECT * FROM (
    SELECT 14001 AS id, 'WJ2026082501' AS test_no, 11001 AS project_id, 1003 AS supplier_id,
           '平阴玫瑰花茶 50g/罐' AS product_name, '量产首批 FL20260820，3 罐×3 份' AS sample_desc,
           '浙江省检验检疫科学技术研究院' AS lab_name, 'CMA' AS lab_qualification,
           '二氧化硫、铅、农药残留18项、菌落总数、大肠菌群、霉菌、致病菌' AS test_items,
           '2026-08-25 09:00:00' AS sent_at, 'HW2026-ROSE-0831' AS report_no, '2026-09-01' AS report_date,
           'PASS' AS conclusion, NULL AS report_attachment_id, 'REPORTED' AS status,
           '全项合格，报告已归档作为放行依据' AS remark, 1 AS tenant_id, 0 AS lock_version,
           5 AS created_by, '2026-08-24 10:00:00' AS created_at, 5 AS updated_by, '2026-09-02 10:00:00' AS updated_at, 0 AS deleted
    UNION ALL SELECT 14002, 'WJ2026091201', 11005, 1004, '阿胶固元糕 15g×15片/盒', '量产首批 MW20260910，6 盒',
           '广东省食品检验所', 'CMA', '酸价、过氧化值、菌落总数、大肠菌群、霉菌、致病菌、净含量',
           '2026-09-12 09:00:00', NULL, NULL, 'PENDING', NULL, 'SENT',
           '已寄样，预计 7 个工作日出报告', 1, 0,
           5, '2026-09-11 10:00:00', 5, '2026-09-12 09:00:00', 0
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_external_test WHERE id = 14001);

-- ---------- 项目阶段时间线 ----------
INSERT INTO qc_npi_timeline (id, project_id, stage, action, title, comment, operator_id, operator_name, created_at, tenant_id)
SELECT * FROM (
    SELECT 16001 AS id, 11001 AS project_id, 'DRAFT' AS stage, 'CREATE' AS action, '新品会立项' AS title,
           '定位高端自营花茶，拟定高/中/低三档标准' AS comment, 5 AS operator_id, '赵主管' AS operator_name, '2026-08-05 11:00:00' AS created_at, 1 AS tenant_id
    UNION ALL SELECT 16002, 11001, 'STD_REVIEW', 'SUBMIT_STD', '三档标准提交评审', '采购与老板评审会签中', 5, '赵主管', '2026-08-08 14:00:00', 1
    UNION ALL SELECT 16003, 11001, 'SOURCING', 'APPROVE_STD', '评审共识达成，按中档标准寻源', '老板批准选中档', 1, '系统管理员', '2026-08-11 16:30:00', 1
    UNION ALL SELECT 16004, 11001, 'SOURCING', 'EVAL_SUBMIT', '送样评估：杭州芳蕾（88.40分）', '对照中档标准合格', 5, '赵主管', '2026-08-15 16:00:00', 1
    UNION ALL SELECT 16005, 11001, 'SOURCING', 'EVAL_SUBMIT', '送样评估：广东美味鲜（80.20分）', '对照中档标准合格，备选', 5, '赵主管', '2026-08-15 16:30:00', 1
    UNION ALL SELECT 16006, 11001, 'AUDIT', 'FIX_SUPPLIER', '定点供应商：杭州芳蕾花茶科技有限公司', '综合评分第一', 1, '系统管理员', '2026-08-16 18:00:00', 1
    UNION ALL SELECT 16007, 11001, 'AUDIT', 'AUDIT_CONFIRM', '验厂结论确认：合格（总分 89.50）', '生产/仓储/质量体系符合', 5, '赵主管', '2026-08-20 17:00:00', 1
    UNION ALL SELECT 16008, 11001, 'PRODUCING', 'AUDIT_PASS', '验厂合格，进入量产', NULL, 1, '系统管理员', '2026-08-20 17:05:00', 1
    UNION ALL SELECT 16009, 11001, 'EXT_TEST', 'SEND_EXT', '量产首批完成，送 CMA 机构检测', NULL, 5, '赵主管', '2026-08-25 09:00:00', 1
    UNION ALL SELECT 16010, 11001, 'EXT_TEST', 'EXT_REPORT', '检测报告回填：浙江省检验检疫科学技术研究院，结论 合格', 'HW2026-ROSE-0831', 5, '赵主管', '2026-09-02 10:00:00', 1
    UNION ALL SELECT 16011, 11001, 'LISTED', 'RELEASE', '前置闸门全部通过，准予上市销售', '关联产品：平阴玫瑰花茶（2004）', 1, '系统管理员', '2026-09-08 10:00:00', 1
    UNION ALL SELECT 16012, 11002, 'DRAFT', 'CREATE', '新品会立项', '三年陈陈皮丝企业定制', 5, '赵主管', '2026-09-01 15:00:00', 1
    UNION ALL SELECT 16013, 11002, 'STD_REVIEW', 'SUBMIT_STD', '三档标准提交评审', '高/中档已采购会签，待老板批准；低档待采购会签', 5, '赵主管', '2026-09-04 10:00:00', 1
    UNION ALL SELECT 16014, 11003, 'DRAFT', 'CREATE', '新品会立项', '金丝皇菊礼盒，性价比路线', 5, '赵主管', '2026-09-03 11:30:00', 1
    UNION ALL SELECT 16015, 11003, 'STD_REVIEW', 'SUBMIT_STD', '三档标准提交评审', NULL, 5, '赵主管', '2026-09-06 11:00:00', 1
    UNION ALL SELECT 16016, 11003, 'SOURCING', 'APPROVE_STD', '评审共识达成，按低档标准寻源', NULL, 1, '系统管理员', '2026-09-09 15:00:00', 1
    UNION ALL SELECT 16017, 11003, 'SOURCING', 'EVAL_SUBMIT', '送样评估：杭州芳蕾（85.00分）', '对照低档标准合格，待定點', 5, '赵主管', '2026-09-13 11:00:00', 1
    UNION ALL SELECT 16018, 11004, 'DRAFT', 'CREATE', '新品会立项', '宁夏特优级枸杞罐装', 5, '赵主管', '2026-09-05 10:30:00', 1
    UNION ALL SELECT 16019, 11004, 'STD_REVIEW', 'SUBMIT_STD', '三档标准提交评审', NULL, 5, '赵主管', '2026-09-08 14:00:00', 1
    UNION ALL SELECT 16020, 11004, 'SOURCING', 'APPROVE_STD', '评审共识达成，按中档标准寻源', NULL, 1, '系统管理员', '2026-09-10 17:00:00', 1
    UNION ALL SELECT 16021, 11004, 'SOURCING', 'EVAL_SUBMIT', '送样评估：宁夏红宝（89.00分）', '对照中档标准合格', 5, '赵主管', '2026-09-13 17:00:00', 1
    UNION ALL SELECT 16022, 11004, 'AUDIT', 'FIX_SUPPLIER', '定点供应商：宁夏中宁红宝枸杞合作社', NULL, 1, '系统管理员', '2026-09-13 18:00:00', 1
    UNION ALL SELECT 16023, 11005, 'DRAFT', 'CREATE', '新品会立项', '受控供应商，须验厂+外检双合格', 5, '赵主管', '2026-08-28 15:00:00', 1
    UNION ALL SELECT 16024, 11005, 'STD_REVIEW', 'SUBMIT_STD', '三档标准提交评审', NULL, 5, '赵主管', '2026-08-31 10:00:00', 1
    UNION ALL SELECT 16025, 11005, 'SOURCING', 'APPROVE_STD', '评审共识达成，按中档标准寻源', NULL, 1, '系统管理员', '2026-09-02 16:00:00', 1
    UNION ALL SELECT 16026, 11005, 'SOURCING', 'EVAL_SUBMIT', '送样评估：广东美味鲜（82.00分）', '合格，以验厂/外检为准', 5, '赵主管', '2026-09-05 16:00:00', 1
    UNION ALL SELECT 16027, 11005, 'AUDIT', 'FIX_SUPPLIER', '定点供应商：广东美味鲜健康食品有限公司（受控）', NULL, 1, '系统管理员', '2026-09-05 17:30:00', 1
    UNION ALL SELECT 16028, 11005, 'AUDIT', 'AUDIT_CONFIRM', '验厂结论确认：合格（总分 86.00）', '整改项已现场确认关闭', 5, '赵主管', '2026-09-09 18:00:00', 1
    UNION ALL SELECT 16029, 11005, 'PRODUCING', 'AUDIT_PASS', '验厂合格，进入量产', NULL, 1, '系统管理员', '2026-09-09 18:05:00', 1
    UNION ALL SELECT 16030, 11005, 'EXT_TEST', 'SEND_EXT', '量产首批寄样送检（广东省食品检验所 CMA）', '预计7个工作日出报告', 5, '赵主管', '2026-09-12 09:00:00', 1
) t
WHERE NOT EXISTS (SELECT 1 FROM qc_npi_timeline WHERE id = 16001);
