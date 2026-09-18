-- =====================================================================
-- V5: 检验标准模板演示种子（四品类各一个 PUBLISHED v1）
--   5001 中药饮片 / 5002 滋补食材 / 5003 花茶 / 5004 健康食品
--   检验项覆盖六分组：感官/包装标签/净含量/理化/微生物/资质资料
-- 注意：限值为演示取值，落地时须按各产品执行标准（药典/GB/GH）逐项校准。
-- =====================================================================

INSERT INTO qc_standard_template
(id, template_code, template_name, category_id, package_form, version, status, max_a_fail, max_b_fail, max_c_fail, concession_allowed, remark, published_by, published_at, tenant_id, lock_version, created_by, created_at, updated_by, updated_at, deleted) VALUES
(5001, 'TPL_TCM',  '中药饮片通用检验标准（袋装/罐装）', 100, NULL, 1, 'PUBLISHED', 0, 2, 3, 1, '演示模板：水分/二氧化硫/微生物限值取药典常用量级，实施按品种校准', 1, NOW(), 1, 0, 1, NOW(), 1, NOW(), 0),
(5002, 'TPL_NOUR', '滋补养生食材通用检验标准',         200, NULL, 1, 'PUBLISHED', 0, 2, 3, 1, '演示模板：果干/枸杞类，二氧化硫按 GB 2760 果干类量级', 1, NOW(), 1, 0, 1, NOW(), 1, NOW(), 0),
(5003, 'TPL_TEA',  '花茶（代用茶）通用检验标准',       300, NULL, 1, 'PUBLISHED', 0, 2, 3, 1, '演示模板：GH/T 1091 代用茶参考量级', 1, NOW(), 1, 0, 1, NOW(), 1, NOW(), 0),
(5004, 'TPL_HF',   '健康食品（即食类）通用检验标准',   400, NULL, 1, 'PUBLISHED', 0, 2, 3, 1, '演示模板：即食/含油食品常用控制限', 1, NOW(), 1, 0, 1, NOW(), 1, NOW(), 0);

-- =====================================================================
-- 检验项（id 段：6001 中药 / 6101 滋补 / 6201 花茶 / 6301 健康食品）
-- =====================================================================
INSERT INTO qc_standard_item
(id, template_id, group_code, item_name, inspect_method, result_type, defect_level, veto_flag, required_flag, min_value, max_value, nominal_value, unit, tolerance_rule, judge_config, sort, tenant_id, created_by, created_at, updated_by, updated_at, deleted) VALUES
-- ============ 5001 中药饮片 ============
(6001, 5001, 'SENSORY',       '色泽均匀，符合该品种应有色泽',         '目检',          'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10,  1, 1, NOW(), 1, NOW(), 0),
(6002, 5001, 'SENSORY',       '气味正常，无异味、无酸败霉味',         '鼻嗅/口尝',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 20,  1, 1, NOW(), 1, NOW(), 0),
(6003, 5001, 'SENSORY',       '形态完整，大小/厚薄均匀，无异常碎末',   '目检',          'QUALITATIVE',  'C', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30,  1, 1, NOW(), 1, NOW(), 0),
(6004, 5001, 'SENSORY',       '无霉变、虫蛀、结块',                   '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 40,  1, 1, NOW(), 1, NOW(), 0),
(6005, 5001, 'SENSORY',       '无杂质、异物（昆虫/毛发/金属/沙石等）', '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50,  1, 1, NOW(), 1, NOW(), 0),
(6006, 5001, 'PACKAGE_LABEL', '包装完整、密封无破损受潮',             '目检/手检',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 60,  1, 1, NOW(), 1, NOW(), 0),
(6007, 5001, 'PACKAGE_LABEL', '标签信息齐全（品名/产地/执行标准/生产许可/储存条件/净含量）', '核对标签', 'QUALITATIVE', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 70, 1, 1, NOW(), 1, NOW(), 0),
(6008, 5001, 'PACKAGE_LABEL', '喷码批号、生产日期清晰且与随货凭证一致', '核对喷码',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 80,  1, 1, NOW(), 1, NOW(), 0),
(6009, 5001, 'PACKAGE_LABEL', '在标示保质期内',                       '日期核算',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 90,  1, 1, NOW(), 1, NOW(), 0),
(6010, 5001, 'NET_CONTENT',   '净含量（按 JJF1070 允许短缺量判定）',  '电子天平称重',  'QUANTITATIVE', 'B', 0, 1, NULL, NULL, NULL, 'g',  'JJF1070', '{"mode":"NET_CONTENT"}', 100, 1, 1, NOW(), 1, NOW(), 0),
(6011, 5001, 'PHYSICO',       '水分',                                 '烘干法',        'QUANTITATIVE', 'B', 0, 1, NULL, 13.0000, NULL, '%',   NULL, NULL, 110, 1, 1, NOW(), 1, NOW(), 0),
(6012, 5001, 'PHYSICO',       '二氧化硫残留量',                       '滴定法',        'QUANTITATIVE', 'A', 0, 1, NULL, 150.0000, NULL, 'mg/kg', NULL, NULL, 120, 1, 1, NOW(), 1, NOW(), 0),
(6013, 5001, 'PHYSICO',       '铅（Pb）',                             'ICP-MS',        'QUANTITATIVE', 'A', 0, 1, NULL, 5.0000, NULL, 'mg/kg', NULL, NULL, 130, 1, 1, NOW(), 1, NOW(), 0),
(6014, 5001, 'MICRO',         '需氧菌总数',                           '平皿计数法',    'QUANTITATIVE', 'B', 0, 1, NULL, 100000.0000, NULL, 'CFU/g', NULL, NULL, 140, 1, 1, NOW(), 1, NOW(), 0),
(6015, 5001, 'MICRO',         '霉菌和酵母菌总数',                     '平皿计数法',    'QUANTITATIVE', 'B', 0, 1, NULL, 500.0000, NULL, 'CFU/g', NULL, NULL, 150, 1, 1, NOW(), 1, NOW(), 0),
(6016, 5001, 'MICRO',         '大肠菌群',                             'MPN法',         'QUANTITATIVE', 'A', 0, 1, NULL, 100.0000, NULL, 'CFU/g', NULL, NULL, 160, 1, 1, NOW(), 1, NOW(), 0),
(6017, 5001, 'MICRO',         '沙门氏菌（不得检出）',                 '定性培养',      'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, '{"operator":"NOT_DETECT"}', 170, 1, 1, NOW(), 1, NOW(), 0),
(6018, 5001, 'CERT_DOC',      '出厂检验报告（同批号）',               '资料核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 180, 1, 1, NOW(), 1, NOW(), 0),
(6019, 5001, 'CERT_DOC',      '生产许可资质（药品生产许可）在有效期内','证照核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 190, 1, 1, NOW(), 1, NOW(), 0),
(6020, 5001, 'CERT_DOC',      '第三方外检报告（标注外检产品必查）',   '资料核验',      'DOCUMENT',     'A', 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, 200, 1, 1, NOW(), 1, NOW(), 0),

-- ============ 5002 滋补养生食材 ============
(6101, 5002, 'SENSORY',       '色泽均匀自然，无异常发黑/发白',        '目检',          'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10,  1, 1, NOW(), 1, NOW(), 0),
(6102, 5002, 'SENSORY',       '气味滋味正常，无异味、无酸败',          '鼻嗅/口尝',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 20,  1, 1, NOW(), 1, NOW(), 0),
(6103, 5002, 'SENSORY',       '果粒/片形完整，碎末少，大小基本均匀',   '目检',          'QUALITATIVE',  'C', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30,  1, 1, NOW(), 1, NOW(), 0),
(6104, 5002, 'SENSORY',       '无霉变、虫蛀、返砂结块异常',            '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 40,  1, 1, NOW(), 1, NOW(), 0),
(6105, 5002, 'SENSORY',       '无杂质、异物（昆虫/毛发/金属/沙石等）', '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50,  1, 1, NOW(), 1, NOW(), 0),
(6106, 5002, 'PACKAGE_LABEL', '包装完整、密封无破损漏气受潮',          '目检/手检',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 60,  1, 1, NOW(), 1, NOW(), 0),
(6107, 5002, 'PACKAGE_LABEL', '标签信息齐全（品名/配料/净含量/产地/执行标准/SC编号/储存条件）', '核对标签', 'QUALITATIVE', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 70, 1, 1, NOW(), 1, NOW(), 0),
(6108, 5002, 'PACKAGE_LABEL', '喷码批号、生产日期清晰且与实物一致',    '核对喷码',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 80,  1, 1, NOW(), 1, NOW(), 0),
(6109, 5002, 'PACKAGE_LABEL', '在标示保质期内',                       '日期核算',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 90,  1, 1, NOW(), 1, NOW(), 0),
(6110, 5002, 'NET_CONTENT',   '净含量（按 JJF1070 允许短缺量判定）',  '电子天平称重',  'QUANTITATIVE', 'B', 0, 1, NULL, NULL, NULL, 'g',  'JJF1070', '{"mode":"NET_CONTENT"}', 100, 1, 1, NOW(), 1, NOW(), 0),
(6111, 5002, 'PHYSICO',       '水分',                                 '烘干法',        'QUANTITATIVE', 'B', 0, 1, NULL, 18.0000, NULL, '%',   NULL, NULL, 110, 1, 1, NOW(), 1, NOW(), 0),
(6112, 5002, 'PHYSICO',       '二氧化硫残留量',                       '滴定法',        'QUANTITATIVE', 'A', 0, 1, NULL, 0.3500, NULL, 'g/kg', NULL, NULL, 120, 1, 1, NOW(), 1, NOW(), 0),
(6113, 5002, 'PHYSICO',       '铅（Pb）',                             'ICP-MS',        'QUANTITATIVE', 'A', 0, 1, NULL, 1.0000, NULL, 'mg/kg', NULL, NULL, 130, 1, 1, NOW(), 1, NOW(), 0),
(6114, 5002, 'MICRO',         '菌落总数',                             '平皿计数法',    'QUANTITATIVE', 'B', 0, 1, NULL, 1000.0000, NULL, 'CFU/g', NULL, NULL, 140, 1, 1, NOW(), 1, NOW(), 0),
(6115, 5002, 'MICRO',         '大肠菌群',                             '平板计数法',    'QUANTITATIVE', 'A', 0, 1, NULL, 10.0000, NULL, 'CFU/g', NULL, NULL, 150, 1, 1, NOW(), 1, NOW(), 0),
(6116, 5002, 'MICRO',         '霉菌',                                 '平皿计数法',    'QUANTITATIVE', 'B', 0, 1, NULL, 50.0000, NULL, 'CFU/g', NULL, NULL, 160, 1, 1, NOW(), 1, NOW(), 0),
(6117, 5002, 'MICRO',         '致病菌（沙门氏菌/金黄色葡萄球菌，不得检出）', '定性培养', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, '{"operator":"NOT_DETECT"}', 170, 1, 1, NOW(), 1, NOW(), 0),
(6118, 5002, 'CERT_DOC',      '出厂检验报告（同批号）',               '资料核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 180, 1, 1, NOW(), 1, NOW(), 0),
(6119, 5002, 'CERT_DOC',      '食品生产许可（SC）资质在有效期内',     '证照核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 190, 1, 1, NOW(), 1, NOW(), 0),
(6120, 5002, 'CERT_DOC',      '第三方外检报告（标注外检产品必查）',   '资料核验',      'DOCUMENT',     'A', 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, 200, 1, 1, NOW(), 1, NOW(), 0),

-- ============ 5003 花茶（代用茶） ============
(6201, 5003, 'SENSORY',       '花朵/花瓣色泽正常，无褐变霉点',        '目检',          'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10,  1, 1, NOW(), 1, NOW(), 0),
(6202, 5003, 'SENSORY',       '香气正常，无霉味、酸馊或其他异味',     '鼻嗅',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 20,  1, 1, NOW(), 1, NOW(), 0),
(6203, 5003, 'SENSORY',       '花形完整，碎瓣率低，无明显虫体',       '目检',          'QUALITATIVE',  'C', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30,  1, 1, NOW(), 1, NOW(), 0),
(6204, 5003, 'SENSORY',       '无霉变、虫蛀、结块',                   '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 40,  1, 1, NOW(), 1, NOW(), 0),
(6205, 5003, 'SENSORY',       '无杂质、异物（昆虫/毛发/金属/沙石等）', '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50,  1, 1, NOW(), 1, NOW(), 0),
(6206, 5003, 'PACKAGE_LABEL', '包装完整、密封无破损受潮',             '目检/手检',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 60,  1, 1, NOW(), 1, NOW(), 0),
(6207, 5003, 'PACKAGE_LABEL', '标签信息齐全（品名/配料/净含量/产地/执行标准/SC编号/储存条件）', '核对标签', 'QUALITATIVE', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 70, 1, 1, NOW(), 1, NOW(), 0),
(6208, 5003, 'PACKAGE_LABEL', '喷码批号、生产日期清晰且与实物一致',    '核对喷码',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 80,  1, 1, NOW(), 1, NOW(), 0),
(6209, 5003, 'PACKAGE_LABEL', '在标示保质期内',                       '日期核算',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 90,  1, 1, NOW(), 1, NOW(), 0),
(6210, 5003, 'NET_CONTENT',   '净含量（按 JJF1070 允许短缺量判定）',  '电子天平称重',  'QUANTITATIVE', 'B', 0, 1, NULL, NULL, NULL, 'g',  'JJF1070', '{"mode":"NET_CONTENT"}', 100, 1, 1, NOW(), 1, NOW(), 0),
(6211, 5003, 'PHYSICO',       '水分',                                 '烘干法',        'QUANTITATIVE', 'B', 0, 1, NULL, 9.0000, NULL, '%',   NULL, NULL, 110, 1, 1, NOW(), 1, NOW(), 0),
(6212, 5003, 'PHYSICO',       '二氧化硫残留量',                       '滴定法',        'QUANTITATIVE', 'A', 0, 1, NULL, 0.3000, NULL, 'g/kg', NULL, NULL, 120, 1, 1, NOW(), 1, NOW(), 0),
(6213, 5003, 'PHYSICO',       '铅（Pb）',                             'ICP-MS',        'QUANTITATIVE', 'A', 0, 1, NULL, 5.0000, NULL, 'mg/kg', NULL, NULL, 130, 1, 1, NOW(), 1, NOW(), 0),
(6214, 5003, 'MICRO',         '菌落总数',                             '平皿计数法',    'QUANTITATIVE', 'C', 0, 1, NULL, 10000.0000, NULL, 'CFU/g', NULL, NULL, 140, 1, 1, NOW(), 1, NOW(), 0),
(6215, 5003, 'MICRO',         '大肠菌群',                             'MPN法',         'QUANTITATIVE', 'B', 0, 1, NULL, 100.0000, NULL, 'CFU/g', NULL, NULL, 150, 1, 1, NOW(), 1, NOW(), 0),
(6216, 5003, 'MICRO',         '霉菌',                                 '平皿计数法',    'QUANTITATIVE', 'C', 0, 1, NULL, 1000.0000, NULL, 'CFU/g', NULL, NULL, 160, 1, 1, NOW(), 1, NOW(), 0),
(6217, 5003, 'CERT_DOC',      '出厂检验报告（同批号）',               '资料核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 180, 1, 1, NOW(), 1, NOW(), 0),
(6218, 5003, 'CERT_DOC',      '食品生产许可（SC）资质在有效期内',     '证照核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 190, 1, 1, NOW(), 1, NOW(), 0),
(6219, 5003, 'CERT_DOC',      '第三方外检报告（标注外检产品必查）',   '资料核验',      'DOCUMENT',     'A', 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, 200, 1, 1, NOW(), 1, NOW(), 0),

-- ============ 5004 健康食品（即食/含油） ============
(6301, 5004, 'SENSORY',       '色泽均匀，符合该产品应有色泽',         '目检',          'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 10,  1, 1, NOW(), 1, NOW(), 0),
(6302, 5004, 'SENSORY',       '气味滋味正常，无哈喇味、酸败或异味',   '鼻嗅/口尝',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 20,  1, 1, NOW(), 1, NOW(), 0),
(6303, 5004, 'SENSORY',       '块形/切片完整，规格均匀',              '目检',          'QUALITATIVE',  'C', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 30,  1, 1, NOW(), 1, NOW(), 0),
(6304, 5004, 'SENSORY',       '无霉变、虫蛀、发黏、结块',             '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 40,  1, 1, NOW(), 1, NOW(), 0),
(6305, 5004, 'SENSORY',       '无杂质、异物（昆虫/毛发/金属/沙石等）', '目检',          'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 50,  1, 1, NOW(), 1, NOW(), 0),
(6306, 5004, 'PACKAGE_LABEL', '内/外包装完整、密封无破损胀袋',        '目检/手检',     'QUALITATIVE',  'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 60,  1, 1, NOW(), 1, NOW(), 0),
(6307, 5004, 'PACKAGE_LABEL', '标签信息齐全（品名/配料表/净含量/产地/执行标准/SC编号/营养成分/储存条件）', '核对标签', 'QUALITATIVE', 'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 70, 1, 1, NOW(), 1, NOW(), 0),
(6308, 5004, 'PACKAGE_LABEL', '喷码批号、生产日期清晰且与实物一致',    '核对喷码',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 80,  1, 1, NOW(), 1, NOW(), 0),
(6309, 5004, 'PACKAGE_LABEL', '在标示保质期内',                       '日期核算',      'QUALITATIVE',  'B', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 90,  1, 1, NOW(), 1, NOW(), 0),
(6310, 5004, 'NET_CONTENT',   '净含量（按 JJF1070 允许短缺量判定）',  '电子天平称重',  'QUANTITATIVE', 'B', 0, 1, NULL, NULL, NULL, 'g',  'JJF1070', '{"mode":"NET_CONTENT"}', 100, 1, 1, NOW(), 1, NOW(), 0),
(6311, 5004, 'PHYSICO',       '水分',                                 '烘干法',        'QUANTITATIVE', 'B', 0, 1, NULL, 15.0000, NULL, '%',   NULL, NULL, 110, 1, 1, NOW(), 1, NOW(), 0),
(6312, 5004, 'PHYSICO',       '铅（Pb）',                             'ICP-MS',        'QUANTITATIVE', 'A', 0, 1, NULL, 1.0000, NULL, 'mg/kg', NULL, NULL, 120, 1, 1, NOW(), 1, NOW(), 0),
(6313, 5004, 'PHYSICO',       '酸价（以脂肪计，KOH）',                '滴定法',        'QUANTITATIVE', 'B', 0, 1, NULL, 3.0000, NULL, 'mg/g', NULL, NULL, 130, 1, 1, NOW(), 1, NOW(), 0),
(6314, 5004, 'MICRO',         '菌落总数',                             '平皿计数法',    'QUANTITATIVE', 'B', 0, 1, NULL, 1000.0000, NULL, 'CFU/g', NULL, NULL, 140, 1, 1, NOW(), 1, NOW(), 0),
(6315, 5004, 'MICRO',         '大肠菌群',                             '平板计数法',    'QUANTITATIVE', 'A', 0, 1, NULL, 10.0000, NULL, 'CFU/g', NULL, NULL, 150, 1, 1, NOW(), 1, NOW(), 0),
(6316, 5004, 'MICRO',         '霉菌',                                 '平皿计数法',    'QUANTITATIVE', 'B', 0, 1, NULL, 50.0000, NULL, 'CFU/g', NULL, NULL, 160, 1, 1, NOW(), 1, NOW(), 0),
(6317, 5004, 'MICRO',         '致病菌（沙门氏菌/金黄色葡萄球菌，不得检出）', '定性培养', 'QUALITATIVE', 'A', 1, 1, NULL, NULL, NULL, NULL, NULL, '{"operator":"NOT_DETECT"}', 170, 1, 1, NOW(), 1, NOW(), 0),
(6318, 5004, 'CERT_DOC',      '出厂检验报告（同批号）',               '资料核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 180, 1, 1, NOW(), 1, NOW(), 0),
(6319, 5004, 'CERT_DOC',      '食品生产许可（SC）资质在有效期内',     '证照核验',      'DOCUMENT',     'A', 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 190, 1, 1, NOW(), 1, NOW(), 0),
(6320, 5004, 'CERT_DOC',      '第三方外检报告（健康食品必查，在有效期内且结论合格）', '资料核验', 'DOCUMENT', 'A', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 200, 1, 1, NOW(), 1, NOW(), 0);
