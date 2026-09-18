-- =====================================================================
-- V15: 脱敏演示数据（以商品为中心的全流程品控台账）
--   1) 8 家虚构供应商 + 36 个虚构商品（覆盖已上市/已过会待上市/审核中/待定/不合作/驳回）；
--   2) 每个商品带流程实例 + 6 个节点记录（部分办结带结论/负责人/自定义字段/关联供应商）；
--   3) 每个商品 8 项资料清单，故意制造缺失/待确认，使工作台缺口统计有内容；
--   4) 全部标记 data_source='DEMO'，“一键清除演示数据”仅删除 DEMO，不触碰 USER 数据。
-- 合规：品牌/厂家/商品名/人员全部虚构，名称统一带“示例/演示/虚构/样板/模拟”字样；
--       批准文号、UPC、电话均为不合规的虚构占位值。
-- 说明：仅新增，不修改 V1~V14 任何对象；依赖 V14 的模板 8001/节点 8101-8106/资料项 8201-8208。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 虚构演示供应商（8 家）
-- ---------------------------------------------------------------------
INSERT INTO qc_supplier
    (id, supplier_code, supplier_name, contact, phone, address, status, data_source,
     tenant_id, created_at, updated_at)
VALUES
(8501, 'SUP-DEMO-01', '示例健康药业有限公司',     '示例对接员甲', '13800000001', '示例市演示区健康路 1 号',   'QUALIFIED',  'DEMO', 1, NOW(), NOW()),
(8502, 'SUP-DEMO-02', '演示制药股份有限公司',     '演示对接员乙', '13800000002', '演示市虚构大道 88 号',      'QUALIFIED',  'DEMO', 1, NOW(), NOW()),
(8503, 'SUP-DEMO-03', '虚构堂中药饮片有限公司',   '虚构对接员丙', '13800000003', '虚构市样板区本草路 16 号',  'QUALIFIED',  'DEMO', 1, NOW(), NOW()),
(8504, 'SUP-DEMO-04', '样板花茶科技有限公司',     '样板对接员丁', '13800000004', '样板市模拟路 66 号',        'CONTROLLED', 'DEMO', 1, NOW(), NOW()),
(8505, 'SUP-DEMO-05', '模拟果源食品合作社',       '模拟对接员戊', '13800000005', '模拟县示例镇果园村 8 号',   'QUALIFIED',  'DEMO', 1, NOW(), NOW()),
(8506, 'SUP-DEMO-06', '演示绿萃生物科技有限公司', '演示对接员己', '13800000006', '演示市示例产业园绿萃大厦',  'QUALIFIED',  'DEMO', 1, NOW(), NOW()),
(8507, 'SUP-DEMO-07', '示例山珍食材有限公司',     '示例对接员庚', '13800000007', '示例市虚构县山珍路 9 号',   'CONTROLLED', 'DEMO', 1, NOW(), NOW()),
(8508, 'SUP-DEMO-08', '虚构康源保健品有限公司',   '虚构对接员辛', '13800000008', '虚构市演示街康源号',        'QUALIFIED',  'DEMO', 1, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 2. 临时表：36 个演示商品的编排数据
--    profile：LAUNCHED 已上市(6节点全办结) / PKG_DONE 已过会待上市(办结至包装) /
--             FACTORY_DONE 验厂完成 / SUPPLIER_DONE 准入完成 / MEETING_DONE 仅过会 /
--             PENDING 待定未过会 / PKG_REJECTED 包装驳回 / NO_COOP 过会不通过
--    stage：已办结节点数（驳回场景 stage=3，第 4 节点单独置 REJECTED）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_goods;
CREATE TEMPORARY TABLE tmp_demo_goods (
    gid          BIGINT NOT NULL,
    sku          VARCHAR(50)  NOT NULL,
    common_name  VARCHAR(200) NOT NULL,
    spec         VARCHAR(150) NULL,
    manufacturer VARCHAR(200) NULL,
    approval_no  VARCHAR(100) NULL,
    upc          VARCHAR(20)  NULL,
    brand        VARCHAR(100) NULL,
    l1           BIGINT NULL,
    l2           BIGINT NULL,
    meeting_date DATE NULL,
    result       VARCHAR(10) NOT NULL,
    launch_date  DATE NULL,
    profile      VARCHAR(16) NOT NULL,
    stage        INT NOT NULL,
    sup_audit    BIGINT NULL,
    sup_factory  BIGINT NULL
) DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_demo_goods VALUES
-- 已上市（生产中/在售），17 个
(900001,'DEMO-001','示例黄芪饮片',     '250g/袋（虚构规格）','虚构堂中药饮片有限公司','示例饮片备字20240001号','6900000000011','虚构堂',100,NULL,'2024-09-12','YES','2024-11-05','LAUNCHED',6,8503,8503),
(900002,'DEMO-002','演示当归饮片',     '200g/盒（虚构规格）','虚构堂中药饮片有限公司','示例饮片备字20240002号','6900000000028','虚构堂',100,NULL,'2024-10-15','YES','2024-12-20','LAUNCHED',6,8503,8503),
(900006,'DEMO-006','示例山药片',       '300g/袋（虚构规格）','示例山珍食材有限公司','示例食材备字20250006号','6900000000066','山珍示例',100,NULL,'2025-02-18','YES','2025-04-10','LAUNCHED',6,8507,8507),
(900007,'DEMO-007','模拟红枣片',       '500g/袋（虚构规格）','模拟果源食品合作社','示例食材备字20240007号','6900000000073','模拟果园',200,201,'2024-11-08','YES','2025-01-15','LAUNCHED',6,8505,8505),
(900008,'DEMO-008','示例枸杞子',       '250g/罐（虚构规格）','模拟果源食品合作社','示例食材备字20240008号','6900000000080','模拟果园',200,201,'2024-12-05','YES','2025-02-18','LAUNCHED',6,8505,8505),
(900012,'DEMO-012','虚构陈皮',         '150g/盒（虚构规格）','虚构堂中药饮片有限公司','示例食材备字20250012号','6900000000127','虚构堂',200,202,'2025-03-20','YES','2025-05-30','LAUNCHED',6,8503,8503),
(900014,'DEMO-014','样板玫瑰花茶',     '3g×20包（虚构规格）','样板花茶科技有限公司','示例代用茶备字20250014号','6900000000141','样板生活',300,301,'2025-01-16','YES','2025-03-25','LAUNCHED',6,8504,8504),
(900016,'DEMO-016','演示菊花茶',       '3g×20包（虚构规格）','演示清源茶业有限公司','示例代用茶备字20250016号','6900000000165','清源演示',300,302,'2025-04-10','YES','2025-06-18','LAUNCHED',6,8502,8502),
(900019,'DEMO-019','示例燕麦麸皮粉',   '500g/袋（虚构规格）','样板田园农业发展有限公司','示例食品备字20250019号','6900000000196','田园样板',400,NULL,'2025-05-22','YES','2025-07-30','LAUNCHED',6,8501,8505),
(900020,'DEMO-020','虚构复合蛋白粉',   '1kg/罐（虚构规格）', '虚构康源保健品有限公司','示例食备字20250020号','6900000000202','康源虚构',400,NULL,'2025-06-12','YES','2025-08-25','LAUNCHED',6,8508,8506),
(900021,'DEMO-021','演示维生素C含片',  '60片/瓶（虚构规格）','示例健康药业有限公司','示例食备字20250021号','6900000000219','示例健康',400,NULL,'2025-07-08','YES','2025-09-16','LAUNCHED',6,8501,8501),
(900023,'DEMO-023','演示益生菌粉',     '2g×30条（虚构规格）','演示绿萃生物科技有限公司','示例食备字20250023号','6900000000233','绿萃演示',400,NULL,'2025-08-14','YES','2025-10-20','LAUNCHED',6,8506,8506),
(900025,'DEMO-025','示例大麦若叶青汁', '3g×30包（虚构规格）','示例本草食品有限公司','示例食备字20250025号','6900000000257','本草示例',400,NULL,'2025-09-02','YES','2025-11-11','LAUNCHED',6,8501,8505),
(900028,'DEMO-028','示例莲子百合羹料', '400g/袋（虚构规格）','示例山珍食材有限公司','示例食材备字20250028号','6900000000288','山珍示例',400,NULL,'2025-09-25','YES','2025-12-05','LAUNCHED',6,8507,8507),
(900031,'DEMO-031','虚构黄精饮片',     '180g/盒（虚构规格）','虚构堂中药饮片有限公司','示例饮片备字20250031号','6900000000318','虚构堂',100,NULL,'2025-10-15','YES','2025-12-28','LAUNCHED',6,8503,8503),
(900034,'DEMO-034','模拟枸杞原浆',     '30ml×10瓶（虚构规格）','模拟果源食品合作社','示例饮品备字20250034号','6900000000349','模拟果园',200,201,'2025-11-06','YES','2026-01-20','LAUNCHED',6,8505,8505),
(900036,'DEMO-036','演示蒲公英茶',     '3g×20包（虚构规格）','演示清源茶业有限公司','示例代用茶备字20250036号','6900000000363','清源演示',300,302,'2025-12-10','YES','2026-02-25','LAUNCHED',6,8502,8502),
-- 已过会待上市（办结至包装审核），4 个
(900003,'DEMO-003','示例党参饮片',     '220g/袋（虚构规格）','虚构堂中药饮片有限公司','示例饮片备字20260003号','6900000000035','虚构堂',100,NULL,'2026-01-08','YES',NULL,'PKG_DONE',4,8503,8503),
(900010,'DEMO-010','模拟蓝莓干',       '80g/袋（虚构规格）', '样板田园农业发展有限公司','示例食材备字20260010号','6900000000103','田园样板',200,201,'2026-01-20','YES',NULL,'PKG_DONE',4,8505,8505),
(900017,'DEMO-017','演示胎菊茶',       '2.5g×20包（虚构规格）','演示清源茶业有限公司','示例代用茶备字20260017号','6900000000172','清源演示',300,302,'2026-02-11','PENDING',NULL,'PKG_DONE',4,8502,8502),
(900022,'DEMO-022','演示钙维生素D片',  '90片/瓶（虚构规格）', '示例健康药业有限公司','示例食备字20260022号','6900000000226','示例健康',400,NULL,'2026-02-26','YES',NULL,'PKG_DONE',4,8501,8501),
-- 实地验厂完成（待包装审核），3 个
(900009,'DEMO-009','模拟桑葚干',       '260g/袋（虚构规格）','模拟果源食品合作社','示例食材备字20260009号','6900000000097','模拟果园',200,201,'2026-03-05','PENDING',NULL,'FACTORY_DONE',3,8505,8505),
(900024,'DEMO-024','虚构胶原蛋白肽饮', '50ml×8瓶（虚构规格）','演示绿萃生物科技有限公司','示例饮品备字20260024号','6900000000240','绿萃演示',400,NULL,'2026-03-18','PENDING',NULL,'FACTORY_DONE',3,8506,8506),
(900032,'DEMO-032','示例百合干',       '200g/袋（虚构规格）','示例山珍食材有限公司','示例食材备字20260032号','6900000000325','山珍示例',200,201,'2026-04-02','PENDING',NULL,'FACTORY_DONE',3,8507,8507),
-- 供应商准入完成（待验厂），3 个
(900004,'DEMO-004','示例茯苓块',       '260g/袋（虚构规格）','虚构堂中药饮片有限公司','示例饮片备字20260004号','6900000000042','虚构堂',100,NULL,'2026-04-15','PENDING',NULL,'SUPPLIER_DONE',2,8503,NULL),
(900015,'DEMO-015','样板玫瑰柠檬茶',   '3g×15包（虚构规格）','样板花茶科技有限公司','示例代用茶备字20260015号','6900000000158','样板生活',300,301,'2026-04-28','PENDING',NULL,'SUPPLIER_DONE',2,8504,NULL),
(900030,'DEMO-030','演示罗汉果茶',     '15g×10枚（虚构规格）','演示清源茶业有限公司','示例代用茶备字20260030号','6900000000301','清源演示',300,302,'2026-05-09','PENDING',NULL,'SUPPLIER_DONE',2,8502,NULL),
-- 仅过会（待供应商准入），3 个
(900011,'DEMO-011','示例桂圆肉',       '220g/盒（虚构规格）','示例山珍食材有限公司','示例食材备字20260011号','6900000000110','山珍示例',200,201,'2026-05-14','PENDING',NULL,'MEETING_DONE',1,NULL,NULL),
(900018,'DEMO-018','样板桂花乌龙茶',   '3g×20包（虚构规格）','样板花茶科技有限公司','示例代用茶备字20260018号','6900000000189','样板生活',300,302,'2026-05-20','PENDING',NULL,'MEETING_DONE',1,NULL,NULL),
(900026,'DEMO-026','演示荞麦茶',       '5g×24包（虚构规格）','演示清源茶业有限公司','示例代用茶备字20260026号','6900000000264','清源演示',400,NULL,'2026-05-27','PENDING',NULL,'MEETING_DONE',1,NULL,NULL),
-- 待定未过会，3 个
(900005,'DEMO-005','虚构甘草片',       '180g/袋（虚构规格）','示例本草食品有限公司',NULL,'6900000000059','本草示例',100,NULL,NULL,'PENDING',NULL,'PENDING',0,NULL,NULL),
(900029,'DEMO-029','样板杂粮粥料包',   '750g/袋（虚构规格）','样板田园农业发展有限公司',NULL,'6900000000295','田园样板',400,NULL,NULL,'PENDING',NULL,'PENDING',0,NULL,NULL),
(900035,'DEMO-035','虚构褪黑素片',     '60片/瓶（虚构规格）','虚构康源保健品有限公司',NULL,'6900000000356','康源虚构',400,NULL,NULL,'PENDING',NULL,'PENDING',0,NULL,NULL),
-- 包装审核驳回（阻塞中），2 个
(900013,'DEMO-013','虚构三年陈皮',     '120g/盒（虚构规格）','虚构堂中药饮片有限公司','示例食材备字20260013号','6900000000134','虚构堂',200,202,'2026-01-22','PENDING',NULL,'PKG_REJECTED',3,8503,8503),
(900027,'DEMO-027','示例山楂条',       '350g/袋（虚构规格）','示例本草食品有限公司','示例食备字20250027号','6900000000271','本草示例',400,NULL,'2026-03-11','PENDING',NULL,'PKG_REJECTED',3,8501,8505),
-- 过会不通过（不合作），1 个
(900033,'DEMO-033','样板玫瑰花酱',     '280g/瓶（虚构规格）','样板花茶科技有限公司',NULL,'6900000000332','样板生活',300,301,'2026-02-06','NO',NULL,'NO_COOP',0,NULL,NULL);

-- ---------------------------------------------------------------------
-- 3. 商品主表（当前节点由 profile/stage 推导；全部 6 节点办结时当前节点置空）
-- ---------------------------------------------------------------------
INSERT INTO qc_ledger_goods
    (id, sku, common_name, spec, manufacturer, approval_no, upc, brand,
     category_l1_id, category_l2_id, meeting_date, cooperate_result, launch_date,
     current_node_code, current_node_name, template_id, template_version,
     data_source, tenant_id, created_at, updated_at)
SELECT
    gid, sku, common_name, spec, manufacturer, approval_no, upc, brand,
    l1, l2, meeting_date, result, launch_date,
    CASE profile
        WHEN 'LAUNCHED' THEN NULL
        WHEN 'PKG_REJECTED' THEN 'PACKAGE_REVIEW'
        WHEN 'NO_COOP' THEN 'MEETING'
        ELSE ELT(stage + 1, 'MEETING', 'SUPPLIER_AUDIT', 'FACTORY_AUDIT',
                 'PACKAGE_REVIEW', 'LISTING', 'POST_MARKET')
    END AS current_node_code,
    CASE profile
        WHEN 'LAUNCHED' THEN NULL
        WHEN 'PKG_REJECTED' THEN '包装审核确认'
        WHEN 'NO_COOP' THEN '过会'
        ELSE ELT(stage + 1, '过会', '供应商准入审核', '实地验厂',
                 '包装审核确认', '上市', '上市后质量监控')
    END AS current_node_name,
    8001, 1, 'DEMO', 1, NOW(), NOW()
FROM tmp_demo_goods;

-- ---------------------------------------------------------------------
-- 4. 流程实例（一个商品一条）
-- ---------------------------------------------------------------------
INSERT INTO qc_ledger_flow (id, goods_id, template_id, template_version, tenant_id, created_at, updated_at)
SELECT 9600000 + (gid - 900000), gid, 8001, 1, 1, NOW(), NOW()
FROM tmp_demo_goods;

-- 6 节点序号临时表
DROP TEMPORARY TABLE IF EXISTS tmp_demo_seq6;
CREATE TEMPORARY TABLE tmp_demo_seq6 (s INT NOT NULL) DEFAULT CHARSET=utf8mb4;
INSERT INTO tmp_demo_seq6 VALUES (1),(2),(3),(4),(5),(6);

-- ---------------------------------------------------------------------
-- 5. 节点记录（状态/完成日期/负责人/结论/关联供应商/自定义字段全部虚构）
-- ---------------------------------------------------------------------
INSERT INTO qc_ledger_flow_node
    (id, flow_id, goods_id, node_code, node_name, node_sort, link_supplier, supplier_id,
     status, finish_date, owner_name, conclusion, field_values, tenant_id, created_at, updated_at)
SELECT
    9100000 + (g.gid - 900000) * 10 + n.s,
    9600000 + (g.gid - 900000),
    g.gid,
    ELT(n.s, 'MEETING', 'SUPPLIER_AUDIT', 'FACTORY_AUDIT', 'PACKAGE_REVIEW', 'LISTING', 'POST_MARKET'),
    ELT(n.s, '过会', '供应商准入审核', '实地验厂', '包装审核确认', '上市', '上市后质量监控'),
    n.s * 10,
    CASE WHEN n.s IN (2, 3) THEN 1 ELSE 0 END,
    CASE
        WHEN n.s = 2 AND x.node_status = 'DONE' THEN g.sup_audit
        WHEN n.s = 3 AND x.node_status = 'DONE' THEN g.sup_factory
        ELSE NULL
    END,
    x.node_status,
    x.finish_date,
    CASE WHEN x.node_status IN ('DONE', 'REJECTED')
         THEN ELT(n.s, '示例品控员', '示例审核员', '示例审核员', '示例品控员', '示例品控员', '示例质量经理')
    END,
    CASE
        WHEN x.node_status = 'REJECTED' AND n.s = 1 THEN '示例：会议表决未通过，暂不合作'
        WHEN x.node_status = 'REJECTED' AND n.s = 4 THEN '示例：包装标签不合规，驳回整改'
        WHEN x.node_status = 'DONE' AND n.s = 1 THEN '示例：会议表决通过，同意推进'
        WHEN x.node_status = 'DONE' AND n.s = 2 THEN '示例：资质齐全，供应商准入通过'
        WHEN x.node_status = 'DONE' AND n.s = 3 THEN '示例：现场审核通过，观察项已闭环'
        WHEN x.node_status = 'DONE' AND n.s = 4 THEN '示例：包装与标签确认通过'
        WHEN x.node_status = 'DONE' AND n.s = 5 THEN '示例：完成首批铺货上市'
        WHEN x.node_status = 'DONE' AND n.s = 6 THEN '示例：上市后监控正常，无质量事件'
    END,
    CASE
        WHEN x.node_status = 'NOT_STARTED' THEN NULL
        WHEN n.s = 1 AND x.node_status = 'REJECTED' THEN '{"batch_no":"示例批次-驳回","vote_result":"未通过"}'
        WHEN n.s = 1 THEN CONCAT('{"batch_no":"示例批次', g.gid, '","vote_result":"全票通过"}')
        WHEN n.s = 2 THEN '{"doc_check":"合格"}'
        WHEN n.s = 3 THEN '{"score":"86","issue_count":"2"}'
        WHEN n.s = 4 AND x.node_status = 'REJECTED' THEN '{"pkg_version":"V0.9示例稿","label_ok":"需整改"}'
        WHEN n.s = 4 THEN '{"pkg_version":"V1.0示例版","label_ok":"合规"}'
        WHEN n.s = 5 THEN CONCAT('{"first_batch":"示例首批', g.gid, '","channel":"全渠道"}')
        WHEN n.s = 6 THEN '{"period":"季度"}'
    END,
    1, NOW(), NOW()
FROM tmp_demo_goods g
JOIN tmp_demo_seq6 n
JOIN (
    -- 派生每个节点的状态与完成日期，避免主语句重复 CASE
    SELECT g2.gid, n2.s,
        CASE
            WHEN g2.profile = 'NO_COOP' AND n2.s = 1 THEN 'REJECTED'
            WHEN g2.profile = 'PKG_REJECTED' AND n2.s = 4 THEN 'REJECTED'
            WHEN g2.profile = 'LAUNCHED' THEN 'DONE'
            WHEN n2.s <= g2.stage THEN 'DONE'
            ELSE 'NOT_STARTED'
        END AS node_status,
        CASE
            WHEN g2.profile = 'NO_COOP' AND n2.s = 1 THEN g2.meeting_date
            WHEN g2.profile = 'PKG_REJECTED' AND n2.s = 4 THEN NULL
            WHEN g2.profile = 'LAUNCHED' THEN
                CASE n2.s
                    WHEN 1 THEN g2.meeting_date
                    WHEN 2 THEN DATE_ADD(g2.meeting_date, INTERVAL 10 DAY)
                    WHEN 3 THEN DATE_ADD(g2.meeting_date, INTERVAL 20 DAY)
                    WHEN 4 THEN DATE_ADD(g2.meeting_date, INTERVAL 30 DAY)
                    WHEN 5 THEN g2.launch_date
                    WHEN 6 THEN DATE_ADD(g2.launch_date, INTERVAL 60 DAY)
                END
            WHEN n2.s <= g2.stage THEN
                CASE n2.s
                    WHEN 1 THEN g2.meeting_date
                    WHEN 2 THEN DATE_ADD(g2.meeting_date, INTERVAL 10 DAY)
                    WHEN 3 THEN DATE_ADD(g2.meeting_date, INTERVAL 20 DAY)
                    WHEN 4 THEN DATE_ADD(g2.meeting_date, INTERVAL 30 DAY)
                END
        END AS finish_date
    FROM tmp_demo_goods g2
    JOIN tmp_demo_seq6 n2
) x ON x.gid = g.gid AND x.s = n.s;

-- ---------------------------------------------------------------------
-- 6. 资料清单（8 项/商品；确定性制造缺失与待确认，保证工作台缺口有数据）
--    规则：资料序号 <= 已办节点数+1 的候选齐套，再按取模挖缺口；
--          MOD(gid+s,11)=0 置待确认；MOD(gid+s,5)=0 置缺失；早期商品自然全缺。
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_seq8;
CREATE TEMPORARY TABLE tmp_demo_seq8 (s INT NOT NULL) DEFAULT CHARSET=utf8mb4;
INSERT INTO tmp_demo_seq8 VALUES (1),(2),(3),(4),(5),(6),(7),(8);

INSERT INTO qc_goods_material
    (id, goods_id, item_id, item_name, status, remark, tenant_id, created_at, updated_at)
SELECT
    9300000 + (g.gid - 900000) * 8 + n.s,
    g.gid,
    8200 + n.s,
    mi.item_name,
    CASE
        WHEN MOD(g.gid + n.s, 11) = 0 THEN 'PENDING'
        WHEN n.s <= g.stage + 1 AND MOD(g.gid + n.s, 5) <> 0 THEN 'READY'
        ELSE 'MISSING'
    END,
    CASE
        WHEN MOD(g.gid + n.s, 11) = 0 THEN '示例：纸质件待确认（虚构）'
        WHEN n.s <= g.stage + 1 AND MOD(g.gid + n.s, 5) <> 0 THEN '示例资料已齐套（虚构）'
        ELSE NULL
    END,
    1, NOW(), NOW()
FROM tmp_demo_goods g
JOIN tmp_demo_seq8 n
JOIN qc_material_item mi ON mi.id = 8200 + n.s AND mi.deleted = 0;

-- 临时表随会话自动释放，显式清理便于同库重复手工演练前的环境对照
DROP TEMPORARY TABLE IF EXISTS tmp_demo_seq8;
DROP TEMPORARY TABLE IF EXISTS tmp_demo_seq6;
DROP TEMPORARY TABLE IF EXISTS tmp_demo_goods;
