-- =====================================================================
-- V2: RBAC 种子（部门/角色/权限/演示账号）+ 数据字典
-- 演示账号：
--   admin / admin123      系统管理员
--   sampler / Qms@12345   抽样收样员
--   inspector / Qms@12345 检验员
--   reviewer / Qms@12345  复核判定人
--   qamanager / Qms@12345 质量主管
-- =====================================================================

-- 部门
INSERT INTO sys_dept (id, parent_id, dept_name, sort, status, tenant_id)
VALUES (1, 0, '品控部', 1, 1, 1);

-- 角色
INSERT INTO sys_role (id, code, name, data_scope, status, remark, tenant_id) VALUES
(1, 'ADMIN',      '系统管理员', 'ALL',      1, '全部权限', 1),
(2, 'SAMPLER',    '抽样收样员', 'ALL',      1, '抽样/收样/留样', 1),
(3, 'INSPECTOR',  '检验员',     'ALL',      1, '逐项检验录入', 1),
(4, 'REVIEWER',   '复核判定人', 'ALL',      1, '复核判定/让步初审', 1),
(5, 'QA_MANAGER', '质量主管',   'ALL',      1, '分配/审批/闭环/看板/配置', 1);

-- ---------------------------------------------------------------------
-- 权限（MENU 菜单 / BUTTON 按钮及接口权限码）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, name, type, path, icon, sort, tenant_id) VALUES
-- 超管通配权限（hasPerm 中 * 匹配所有权限码）
(1,   0,   '*',                       '全部权限(超管)', 'API', NULL, NULL, 0, 1),
-- 工作台
(101, 0,   'workbench:view',        '品控工作台', 'MENU', '/workbench', 'DashboardOutlined', 10, 1),
-- 抽样/样品
(201, 0,   'sampling:list',         '抽样管理',   'MENU', '/sampling',  'AuditOutlined', 20, 1),
(202, 201, 'sampling:create',       '新建抽样单', 'BUTTON', NULL, NULL, 1, 1),
(203, 201, 'sampling:submit',       '提交抽样单', 'BUTTON', NULL, NULL, 2, 1),
(204, 201, 'sampling:cancel',       '撤销抽样单', 'BUTTON', NULL, NULL, 3, 1),
(205, 201, 'sampling:view',         '抽样单详情', 'BUTTON', NULL, NULL, 4, 1),
(211, 0,   'sample:list',           '样品管理',   'MENU', '/samples',   'BarcodeOutlined', 21, 1),
(212, 211, 'sample:receive',        '收样登记',   'BUTTON', NULL, NULL, 1, 1),
(213, 211, 'sample:view',           '样品详情',   'BUTTON', NULL, NULL, 2, 1),
(214, 211, 'sample:retain',         '留样登记',   'BUTTON', NULL, NULL, 3, 1),
(215, 211, 'sample:dispose',        '留样处置',   'BUTTON', NULL, NULL, 4, 1),
-- 检验
(301, 0,   'inspection:task:list',  '检验任务',   'MENU', '/inspection/tasks', 'ExperimentOutlined', 30, 1),
(302, 301, 'inspection:task:assign','任务分配',   'BUTTON', NULL, NULL, 1, 1),
(303, 301, 'inspection:task:handle','开始/执行检验','BUTTON', NULL, NULL, 2, 1),
(304, 301, 'inspection:task:view',  '任务详情',   'BUTTON', NULL, NULL, 3, 1),
(305, 301, 'inspection:task:submit','提交复核',   'BUTTON', NULL, NULL, 4, 1),
(306, 301, 'inspection:task:review','复核判定',   'BUTTON', NULL, NULL, 5, 1),
(307, 301, 'inspection:task:recheck','发起复检',  'BUTTON', NULL, NULL, 6, 1),
(311, 0,   'inspection:result:list','检验结果查看','BUTTON', NULL, NULL, 7, 1),
(312, 0,   'inspection:result:save','检验结果录入','BUTTON', NULL, NULL, 8, 1),
(321, 0,   'inspection:report:view','质检报告查看','MENU', '/inspection/reports', 'FileTextOutlined', 31, 1),
(322, 321, 'inspection:report:export','报告导出PDF','BUTTON', NULL, NULL, 1, 1),
-- 不合格闭环
(401, 0,   'defect:list',           '不合格处置', 'MENU', '/defect', 'WarningOutlined', 40, 1),
(402, 401, 'defect:view',           '不合格单详情','BUTTON', NULL, NULL, 1, 1),
(403, 401, 'defect:review',         '质量评审',   'BUTTON', NULL, NULL, 2, 1),
(404, 401, 'defect:approve',        '处置审批',   'BUTTON', NULL, NULL, 3, 1),
(405, 401, 'defect:execute',        '处置执行',   'BUTTON', NULL, NULL, 4, 1),
(406, 401, 'defect:concession',     '让步接收',   'BUTTON', NULL, NULL, 5, 1),
(407, 401, 'defect:close',          '闭环确认',   'BUTTON', NULL, NULL, 6, 1),
(411, 0,   'rect:list',             '供应商整改', 'MENU', '/rectification', 'SolutionOutlined', 41, 1),
(412, 411, 'rect:view',             '整改单详情', 'BUTTON', NULL, NULL, 1, 1),
(413, 411, 'rect:save',             '整改单填写', 'BUTTON', NULL, NULL, 2, 1),
(414, 411, 'rect:verify',           '整改验证',   'BUTTON', NULL, NULL, 3, 1),
-- 主数据
(501, 0,   'master:product:list',   '产品档案',   'MENU', '/master/products', 'ShopOutlined', 50, 1),
(502, 501, 'master:product:create', '新增产品',   'BUTTON', NULL, NULL, 1, 1),
(503, 501, 'master:product:edit',   '编辑产品',   'BUTTON', NULL, NULL, 2, 1),
(504, 501, 'master:product:sku',    'SKU维护',    'BUTTON', NULL, NULL, 3, 1),
(505, 501, 'master:product:status', '质量状态变更','BUTTON', NULL, NULL, 4, 1),
(511, 0,   'master:supplier:list',  '供应商管理', 'MENU', '/master/suppliers', 'TeamOutlined', 51, 1),
(512, 511, 'master:supplier:create','新增供应商', 'BUTTON', NULL, NULL, 1, 1),
(513, 511, 'master:supplier:edit',  '编辑供应商', 'BUTTON', NULL, NULL, 2, 1),
(514, 511, 'master:supplier:license','供应商资质','BUTTON', NULL, NULL, 3, 1),
(515, 511, 'master:supplier:view',  '供应商查看', 'BUTTON', NULL, NULL, 4, 1),
(521, 0,   'master:category:list',  '品类管理',   'MENU', '/master/categories', 'AppstoreOutlined', 52, 1),
(522, 521, 'master:category:create','新增品类',  'BUTTON', NULL, NULL, 1, 1),
(523, 521, 'master:category:edit',  '编辑品类',   'BUTTON', NULL, NULL, 2, 1),
(524, 521, 'master:category:delete','删除品类',  'BUTTON', NULL, NULL, 3, 1),
-- 标准/流程配置
(601, 0,   'std:template:list',     '检验标准库', 'MENU', '/standard/templates', 'ProfileOutlined', 60, 1),
(602, 601, 'std:template:edit',     '模板编辑',   'BUTTON', NULL, NULL, 1, 1),
(603, 601, 'std:template:publish',  '模板发布',   'BUTTON', NULL, NULL, 2, 1),
(611, 0,   'std:item:list',         '检验项查看', 'BUTTON', NULL, NULL, 3, 1),
(612, 0,   'std:item:edit',         '检验项维护', 'BUTTON', NULL, NULL, 4, 1),
(621, 0,   'process:def:list',      '品控流程配置','MENU', '/process', 'BranchesOutlined', 61, 1),
(622, 621, 'process:def:edit',      '流程定义编辑','BUTTON', NULL, NULL, 1, 1),
(623, 621, 'process:def:publish',   '流程发布',   'BUTTON', NULL, NULL, 2, 1),
(624, 621, 'process:node:edit',     '流程节点编排','BUTTON', NULL, NULL, 3, 1),
-- 看板/报表
(701, 0,   'dashboard:view',        '质量看板',   'MENU', '/dashboard', 'BarChartOutlined', 70, 1),
(702, 0,   'report:export',         '质量月报导出','BUTTON', NULL, NULL, 1, 1),
-- 预警
(801, 0,   'alert:list',            '预警查看',   'BUTTON', NULL, NULL, 1, 1),
(802, 0,   'alert:handle',          '预警处理',   'BUTTON', NULL, NULL, 2, 1),
(803, 0,   'alert:scan',            '预警手动扫描','BUTTON', NULL, NULL, 3, 1),
-- 系统管理
(901, 0,   'system:user:list',      '用户管理',   'MENU', '/system/users', 'UserOutlined', 90, 1),
(902, 901, 'system:user:create',    '新增用户',   'BUTTON', NULL, NULL, 1, 1),
(903, 901, 'system:user:edit',      '编辑用户',   'BUTTON', NULL, NULL, 2, 1),
(904, 901, 'system:user:reset',     '重置密码',   'BUTTON', NULL, NULL, 3, 1),
(911, 0,   'system:role:list',      '角色权限',   'MENU', '/system/roles', 'SafetyOutlined', 91, 1),
(912, 911, 'system:role:edit',      '角色编辑授权','BUTTON', NULL, NULL, 1, 1),
(921, 0,   'system:dept:list',      '部门管理',   'MENU', '/system/depts', 'ApartmentOutlined', 92, 1),
(931, 0,   'system:dict:list',      '数据字典',   'MENU', '/system/dicts', 'BookOutlined', 93, 1),
(941, 0,   'audit:list',            '操作审计日志','MENU', '/system/audit-logs', 'FileSearchOutlined', 94, 1),
(942, 0,   'audit:login',           '登录日志',   'MENU', '/system/login-logs', 'LoginOutlined', 95, 1);

-- ---------------------------------------------------------------------
-- 角色-权限
-- ---------------------------------------------------------------------
-- ADMIN 全部
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 抽样收样员
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE perm_code IN (
    'workbench:view',
    'sampling:list','sampling:create','sampling:submit','sampling:cancel','sampling:view',
    'sample:list','sample:receive','sample:view','sample:retain','sample:dispose',
    'inspection:task:list','inspection:task:view','inspection:result:list','inspection:report:view',
    'defect:list','defect:view',
    'master:category:list','master:product:list','master:supplier:list',
    'dashboard:view','alert:list','alert:handle'
);

-- 检验员
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE perm_code IN (
    'workbench:view',
    'inspection:task:list','inspection:task:handle','inspection:task:view',
    'inspection:task:submit','inspection:task:recheck',
    'inspection:result:list','inspection:result:save','inspection:report:view','inspection:report:export',
    'sampling:list','sampling:view','sample:list','sample:view',
    'defect:list','defect:view',
    'master:category:list','master:product:list',
    'dashboard:view','alert:list','alert:handle'
);

-- 复核判定人
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 4, id FROM sys_permission WHERE perm_code IN (
    'workbench:view',
    'inspection:task:list','inspection:task:view','inspection:task:review','inspection:task:recheck',
    'inspection:result:list','inspection:report:view','inspection:report:export',
    'sampling:list','sampling:view','sample:list','sample:view',
    'defect:list','defect:view','defect:review',
    'master:category:list','master:product:list',
    'dashboard:view','alert:list','alert:handle'
);

-- 质量主管（业务全覆盖 + 审计查看，不含系统用户/角色管理）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 5, id FROM sys_permission
WHERE perm_code LIKE 'workbench:%'
   OR perm_code LIKE 'sampling:%'
   OR perm_code LIKE 'sample:%'
   OR perm_code LIKE 'inspection:%'
   OR perm_code LIKE 'defect:%'
   OR perm_code LIKE 'rect:%'
   OR perm_code LIKE 'master:%'
   OR perm_code LIKE 'std:%'
   OR perm_code LIKE 'process:%'
   OR perm_code LIKE 'dashboard:%'
   OR perm_code LIKE 'report:%'
   OR perm_code LIKE 'alert:%'
   OR perm_code IN ('audit:list','audit:login');

-- ---------------------------------------------------------------------
-- 演示用户（密码哈希为 BCrypt，由迁移时内置；见下方实际值）
-- ---------------------------------------------------------------------
INSERT INTO sys_user (id, dept_id, username, real_name, password_hash, phone, email, status, tenant_id) VALUES
(1, 1, 'admin',     '系统管理员', '$2a$10$Fg2LQgdEKvHfbR/a5vyav.cAGzFqTdqEDgPUp5PeTFr.0oF.vIgiK', '13800000001', 'admin@qingyuan.test',     1, 1),
(2, 1, 'sampler',   '张抽样',     '$2a$10$tgmoQ.yesWd3uZlCZlukhOwASBexWqYUnU0wQtYgaxmkYqPWRrQ.S', '13800000002', 'sampler@qingyuan.test',   1, 1),
(3, 1, 'inspector', '李检验',     '$2a$10$tgmoQ.yesWd3uZlCZlukhOwASBexWqYUnU0wQtYgaxmkYqPWRrQ.S', '13800000003', 'inspector@qingyuan.test', 1, 1),
(4, 1, 'reviewer',  '王复核',     '$2a$10$tgmoQ.yesWd3uZlCZlukhOwASBexWqYUnU0wQtYgaxmkYqPWRrQ.S', '13800000004', 'reviewer@qingyuan.test',  1, 1),
(5, 1, 'qamanager', '赵主管',     '$2a$10$tgmoQ.yesWd3uZlCZlukhOwASBexWqYUnU0wQtYgaxmkYqPWRrQ.S', '13800000005', 'qa@qingyuan.test',        1, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 5);

-- =====================================================================
-- 数据字典
-- =====================================================================
INSERT INTO sys_dict_type (id, type_code, type_name, status, tenant_id) VALUES
(1, 'sampling_source',  '抽样来源',   1, 1),
(2, 'sample_type',      '样品类型',   1, 1),
(3, 'sample_status',    '样品状态',   1, 1),
(4, 'sampling_status',  '抽样单状态', 1, 1),
(5, 'task_status',      '检验任务状态',1, 1),
(6, 'report_conclusion','报告结论',   1, 1),
(7, 'defect_level',     '缺陷等级',   1, 1),
(8, 'disposition',      '处置方式',   1, 1),
(9, 'package_form',     '包装形态',   1, 1),
(10,'result_type',      '结果类型',   1, 1),
(11,'item_group',       '检验项分组', 1, 1),
(12,'quality_status',   '产品质量状态',1, 1),
(13,'supplier_status',  '供应商状态', 1, 1),
(14,'config_status',    '配置版本状态',1, 1),
(15,'batch_status',     '批次状态',   1, 1);

INSERT INTO sys_dict_item (id, type_code, item_value, item_label, sort, status, tenant_id) VALUES
(1001,'sampling_source','NEW_ADMISSION','新品准入',1,1,1),
(1002,'sampling_source','INCOMING_BATCH','来货批检',2,1,1),
(1003,'sampling_source','PERIODIC','在售周期抽检',3,1,1),
(1004,'sampling_source','COMPLAINT','客诉专项',4,1,1),
(1005,'sampling_source','FLYING','飞行抽检',5,1,1),
(1006,'sample_type','INSPECTION','检验样',1,1,1),
(1007,'sample_type','RETAIN','留样',2,1,1),
(1008,'sample_type','BACKUP','备样',3,1,1),
(1009,'sample_status','PENDING_RECEIVE','待收样',1,1,1),
(1010,'sample_status','IN_INSPECTION','在检',2,1,1),
(1011,'sample_status','RETAINING','留样中',3,1,1),
(1012,'sample_status','DISPOSED','已处置',4,1,1),
(1013,'sampling_status','DRAFT','草稿',1,1,1),
(1014,'sampling_status','PENDING_RECEIVE','待收样',2,1,1),
(1015,'sampling_status','RECEIVED','已收样',3,1,1),
(1016,'sampling_status','ARCHIVED','已归档',4,1,1),
(1017,'sampling_status','CANCELLED','已撤销',5,1,1),
(1018,'task_status','PENDING_ASSIGN','待分配',1,1,1),
(1019,'task_status','PENDING_INSPECT','待检',2,1,1),
(1020,'task_status','INSPECTING','检验中',3,1,1),
(1021,'task_status','PENDING_REVIEW','待复核',4,1,1),
(1022,'task_status','JUDGED','已判定',5,1,1),
(1023,'task_status','RECHECKING','复检中',6,1,1),
(1024,'task_status','CLOSED','已闭环',7,1,1),
(1025,'report_conclusion','QUALIFIED','合格',1,1,1),
(1026,'report_conclusion','UNQUALIFIED','不合格',2,1,1),
(1027,'report_conclusion','CONCESSION','让步接收',3,1,1),
(1028,'report_conclusion','PENDING_RECHECK','待复检',4,1,1),
(1029,'defect_level','A','严重(A)',1,1,1),
(1030,'defect_level','B','主要(B)',2,1,1),
(1031,'defect_level','C','次要(C)',3,1,1),
(1032,'disposition','RETURN','退货',1,1,1),
(1033,'disposition','OFF_SHELF','下架',2,1,1),
(1034,'disposition','DESTROY','销毁',3,1,1),
(1035,'disposition','EXCHANGE','换货',4,1,1),
(1036,'disposition','RECTIFY','供应商整改',5,1,1),
(1037,'disposition','CONCESSION','让步接收',6,1,1),
(1038,'package_form','袋装','袋装',1,1,1),
(1039,'package_form','罐装','罐装',2,1,1),
(1040,'package_form','盒装','盒装',3,1,1),
(1041,'result_type','QUALITATIVE','定性',1,1,1),
(1042,'result_type','QUANTITATIVE','定量',2,1,1),
(1043,'result_type','DOCUMENT','资料上传',3,1,1),
(1044,'item_group','SENSORY','感官指标',1,1,1),
(1045,'item_group','PACKAGE_LABEL','包装与标签标识',2,1,1),
(1046,'item_group','NET_CONTENT','净含量',3,1,1),
(1047,'item_group','PHYSICO','理化指标',4,1,1),
(1048,'item_group','MICRO','微生物',5,1,1),
(1049,'item_group','CERT_DOC','资质与外检报告',6,1,1),
(1050,'quality_status','NORMAL','正常',1,1,1),
(1051,'quality_status','CONTROLLED','受控',2,1,1),
(1052,'quality_status','FROZEN','暂停采购销售',3,1,1),
(1053,'quality_status','DISABLED','淘汰',4,1,1),
(1054,'supplier_status','QUALIFIED','合格',1,1,1),
(1055,'supplier_status','CONTROLLED','受控',2,1,1),
(1056,'supplier_status','DISABLED','禁用',3,1,1),
(1057,'config_status','DRAFT','草稿',1,1,1),
(1058,'config_status','PUBLISHED','已发布',2,1,1),
(1059,'config_status','ARCHIVED','已归档',3,1,1),
(1060,'batch_status','NORMAL','正常',1,1,1),
(1061,'batch_status','NEAR_EXPIRY','近效期',2,1,1),
(1062,'batch_status','EXPIRED','已过期',3,1,1);
