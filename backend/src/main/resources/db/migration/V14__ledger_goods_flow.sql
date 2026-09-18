-- =====================================================================
-- V14: 以商品为中心的全流程品控台账
-- 包含：商品台账主表、流程模板（模板/节点/节点字段）、商品流程实例与节点记录、
--       资料项定义与商品资料清单；并为既有供应商表补充“数据来源”标记。
-- 约定：
--   1) 不修改 V1~V13 的任何对象，只新增表/字段；
--   2) 业务表沿用 tenant_id/审计列/逻辑删；关键单据带 lock_version；
--   3) data_source：USER=用户建档/导入，DEMO=脱敏演示数据（一键清除仅删 DEMO）；
--   4) 商品关联模板版本：新建商品时把模板节点整体快照到节点记录表，
--      模板后续改动不追溯已建商品（仅在页面提示）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 既有供应商表增加数据来源标记（历史数据一律视为 USER，不参与演示清除）
-- ---------------------------------------------------------------------
ALTER TABLE qc_supplier
    ADD COLUMN data_source VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT 'USER用户数据/DEMO演示数据' AFTER status;

-- ---------------------------------------------------------------------
-- 1. 商品台账主表
-- ---------------------------------------------------------------------
CREATE TABLE qc_ledger_goods (
    id                  BIGINT       NOT NULL COMMENT '主键(雪花)',
    sku                 VARCHAR(50)  NOT NULL COMMENT 'SKU 编码（租户内唯一，导入按此匹配）',
    common_name         VARCHAR(200) NOT NULL COMMENT '产品通用名',
    spec                VARCHAR(150) NULL COMMENT '规格',
    manufacturer        VARCHAR(200) NULL COMMENT '生产企业',
    approval_no         VARCHAR(100) NULL COMMENT '批准文号/备案号',
    upc                 VARCHAR(20)  NULL COMMENT 'UPC(69码)',
    brand               VARCHAR(100) NULL COMMENT '品牌',
    category_l1_id      BIGINT       NULL COMMENT '一级品类 id（关联 qc_category）',
    category_l2_id      BIGINT       NULL COMMENT '二级品类 id（关联 qc_category）',
    meeting_date        DATE         NULL COMMENT '过会时间',
    cooperate_result    VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT '合作结论 YES是/PENDING待定/NO否',
    launch_date         DATE         NULL COMMENT '上市日期',
    current_node_code   VARCHAR(50)  NULL COMMENT '当前流程节点编码（快照，便于列表筛选）',
    current_node_name   VARCHAR(100) NULL COMMENT '当前流程节点名称（快照）',
    template_id         BIGINT       NULL COMMENT '关联流程模板 id',
    template_version    INT          NULL COMMENT '建档时模板版本（快照）',
    npi_project_id      BIGINT       NULL COMMENT '可选关联新品引入项目 id（不强制）',
    data_source         VARCHAR(10)  NOT NULL DEFAULT 'USER' COMMENT 'USER/DEMO',
    remark              VARCHAR(500) NULL COMMENT '备注',
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    lock_version        INT          NOT NULL DEFAULT 0,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_goods_sku (tenant_id, sku),
    KEY idx_lg_category_l1 (category_l1_id),
    KEY idx_lg_category_l2 (category_l2_id),
    KEY idx_lg_result (cooperate_result),
    KEY idx_lg_node (current_node_code),
    KEY idx_lg_brand (brand),
    KEY idx_lg_manufacturer (manufacturer),
    KEY idx_lg_source (data_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品品控台账主表';

-- ---------------------------------------------------------------------
-- 2. 流程模板
-- ---------------------------------------------------------------------
CREATE TABLE qc_flow_template (
    id              BIGINT       NOT NULL,
    template_code   VARCHAR(50)  NOT NULL COMMENT '模板编码',
    template_name   VARCHAR(100) NOT NULL COMMENT '模板名称',
    version         INT          NOT NULL DEFAULT 1 COMMENT '版本号（节点变更时+1）',
    is_preset       TINYINT      NOT NULL DEFAULT 0 COMMENT '1系统预置 0自建',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0停用',
    remark          VARCHAR(255) NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    lock_version    INT          NOT NULL DEFAULT 0,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_tpl_code_ver (tenant_id, template_code, version),
    KEY idx_ft_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品全流程模板';

-- ---------------------------------------------------------------------
-- 3. 模板节点定义（商品建档时按节点整体快照，模板后续编辑不追溯历史商品）
-- ---------------------------------------------------------------------
CREATE TABLE qc_flow_node_def (
    id              BIGINT       NOT NULL,
    template_id     BIGINT       NOT NULL COMMENT '所属模板（含版本行）',
    node_code       VARCHAR(50)  NOT NULL COMMENT '节点编码（同一模板版本内唯一）',
    node_name       VARCHAR(100) NOT NULL COMMENT '节点名称',
    node_sort       INT          NOT NULL DEFAULT 0 COMMENT '节点顺序（从小到大）',
    link_supplier   TINYINT      NOT NULL DEFAULT 0 COMMENT '该节点是否关联供应商（准入/验厂）1是0否',
    remark          VARCHAR(255) NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fnd_code (template_id, node_code),
    KEY idx_fnd_tpl (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板节点定义';

-- ---------------------------------------------------------------------
-- 4. 模板节点自定义字段配置
-- ---------------------------------------------------------------------
CREATE TABLE qc_flow_node_field_def (
    id              BIGINT       NOT NULL,
    node_def_id     BIGINT       NOT NULL,
    field_code      VARCHAR(50)  NOT NULL COMMENT '字段编码（节点内唯一）',
    field_name      VARCHAR(100) NOT NULL COMMENT '字段名',
    field_type      VARCHAR(12)  NOT NULL COMMENT 'TEXT文本/DATE日期/SELECT下拉/FILE附件/CONCLUSION结论/TEXTAREA多行文本',
    options_json     VARCHAR(1000) NULL COMMENT 'SELECT 下拉选项 JSON 数组，如 ["选项A","选项B"]',
    required        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填 1是0否',
    sort            INT          NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_fnfd_node (node_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点自定义字段定义';

-- ---------------------------------------------------------------------
-- 5. 商品流程实例（一个商品一条）
-- ---------------------------------------------------------------------
CREATE TABLE qc_ledger_flow (
    id                  BIGINT   NOT NULL,
    goods_id            BIGINT   NOT NULL COMMENT '商品 id',
    template_id         BIGINT   NOT NULL COMMENT '建档时所用模板版本行 id',
    template_version    INT      NOT NULL COMMENT '建档时模板版本',
    tenant_id           BIGINT   NOT NULL DEFAULT 1,
    created_by          BIGINT   NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT   NULL,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lf_goods (goods_id),
    KEY idx_lf_tpl (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品全流程实例';

-- ---------------------------------------------------------------------
-- 6. 商品流程节点记录（建档时由模板快照生成；自定义字段值以 JSON 存 field_values）
-- ---------------------------------------------------------------------
CREATE TABLE qc_ledger_flow_node (
    id              BIGINT       NOT NULL,
    flow_id         BIGINT       NOT NULL COMMENT '流程实例 id',
    goods_id        BIGINT       NOT NULL,
    node_code       VARCHAR(50)  NOT NULL,
    node_name       VARCHAR(100) NOT NULL,
    node_sort       INT          NOT NULL DEFAULT 0,
    link_supplier   TINYINT      NOT NULL DEFAULT 0,
    supplier_id     BIGINT       NULL COMMENT '关联供应商（准入/验厂节点）',
    status          VARCHAR(12)  NOT NULL DEFAULT 'NOT_STARTED'
                    COMMENT 'NOT_STARTED未开始/IN_PROGRESS进行中/DONE已完成/REJECTED不通过',
    finish_date     DATE         NULL COMMENT '完成日期',
    owner_name      VARCHAR(64)  NULL COMMENT '负责人（姓名）',
    conclusion      VARCHAR(255) NULL COMMENT '结论',
    remark          VARCHAR(1000) NULL COMMENT '备注',
    field_values    TEXT         NULL COMMENT '自定义字段值 JSON：{fieldCode:值}',
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lfn_goods_node (goods_id, node_code),
    KEY idx_lfn_flow (flow_id),
    KEY idx_lfn_supplier (supplier_id),
    KEY idx_lfn_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品全流程节点记录';

-- ---------------------------------------------------------------------
-- 7. 资料项定义（系统预置 + 管理员可维护）
-- ---------------------------------------------------------------------
CREATE TABLE qc_material_item (
    id              BIGINT       NOT NULL,
    item_code       VARCHAR(50)  NOT NULL,
    item_name       VARCHAR(100) NOT NULL COMMENT '资料项名称（产前样品/型式检验报告等）',
    sort            INT          NOT NULL DEFAULT 0,
    is_preset       TINYINT      NOT NULL DEFAULT 0 COMMENT '1系统预置 0自建',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0停用',
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mi_code (tenant_id, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品资料项定义';

-- ---------------------------------------------------------------------
-- 8. 商品资料清单（每个商品按启用的资料项生成；缺失/待确认即重点缺口）
-- ---------------------------------------------------------------------
CREATE TABLE qc_goods_material (
    id              BIGINT       NOT NULL,
    goods_id        BIGINT       NOT NULL,
    item_id         BIGINT       NOT NULL COMMENT '资料项 id',
    item_name       VARCHAR(100) NOT NULL COMMENT '资料项名称快照',
    status          VARCHAR(10)  NOT NULL DEFAULT 'MISSING' COMMENT 'READY齐套/MISSING缺失/PENDING待确认',
    remark          VARCHAR(500) NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gm_goods_item (tenant_id, goods_id, item_id),
    KEY idx_gm_goods (goods_id),
    KEY idx_gm_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品资料清单';

-- =====================================================================
-- 权限（1200 段：商品全流程品控台账）
-- =====================================================================
INSERT INTO sys_permission (id, parent_id, perm_code, name, type, path, icon, sort, tenant_id) VALUES
(1201, 0,    'ledger:goods:list',    '商品台账',   'MENU', '/ledger/goods',          'ProfileOutlined', 11, 1),
(1202, 1201, 'ledger:goods:view',    '商品详情',   'BUTTON', NULL, NULL, 1, 1),
(1203, 1201, 'ledger:goods:create',  '商品建档',   'BUTTON', NULL, NULL, 2, 1),
(1204, 1201, 'ledger:goods:edit',    '商品编辑',   'BUTTON', NULL, NULL, 3, 1),
(1205, 1201, 'ledger:goods:delete',  '商品删除',   'BUTTON', NULL, NULL, 4, 1),
(1206, 1201, 'ledger:goods:import',  'Excel导入',  'BUTTON', NULL, NULL, 5, 1),
(1207, 1201, 'ledger:goods:export',  '台账导出',   'BUTTON', NULL, NULL, 6, 1),
(1211, 0,    'ledger:template:list', '流程模板配置','MENU', '/ledger/templates',      'BranchesOutlined', 12, 1),
(1212, 1211, 'ledger:template:edit', '模板编辑',   'BUTTON', NULL, NULL, 1, 1),
(1213, 1211, 'ledger:material:edit', '资料项维护', 'BUTTON', NULL, NULL, 2, 1),
(1221, 0,    'ledger:demo:view',     '演示数据管理','MENU', '/ledger/demo-setting',   'DeleteOutlined', 90, 1),
(1222, 1221, 'ledger:demo:clear',    '清除演示数据','BUTTON', NULL, NULL, 1, 1);

-- ADMIN 通配 * 已覆盖；业务角色授予台账只读/详情/导出（写操作默认仅管理员）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM sys_permission p
JOIN (
    SELECT 2 AS role_id, 'ledger:goods:list' AS perm_code
    UNION ALL SELECT 2, 'ledger:goods:view'
    UNION ALL SELECT 3, 'ledger:goods:list'
    UNION ALL SELECT 3, 'ledger:goods:view'
    UNION ALL SELECT 4, 'ledger:goods:list'
    UNION ALL SELECT 4, 'ledger:goods:view'
    UNION ALL SELECT 5, 'ledger:goods:list'
    UNION ALL SELECT 5, 'ledger:goods:view'
    UNION ALL SELECT 5, 'ledger:goods:export'
    UNION ALL SELECT 5, 'ledger:template:list'
) r ON r.perm_code = p.perm_code;

-- =====================================================================
-- 预置：默认全流程示例模板（可复制修改，不强制使用）
-- =====================================================================
INSERT INTO qc_flow_template
    (id, template_code, template_name, version, is_preset, status, remark, tenant_id, created_at, updated_at)
VALUES
    (8001, 'DEFAULT_GOODS_FLOW', '商品全流程默认模板（示例）', 1, 1, 1,
     '系统预置示例：过会→供应商准入审核→实地验厂→包装审核确认→上市→上市后质量监控，可复制后修改',
     1, NOW(), NOW());

-- 默认模板六个节点
INSERT INTO qc_flow_node_def
    (id, template_id, node_code, node_name, node_sort, link_supplier, remark, tenant_id, created_at, updated_at)
VALUES
    (8101, 8001, 'MEETING',        '过会',             10, 0, NULL, 1, NOW(), NOW()),
    (8102, 8001, 'SUPPLIER_AUDIT', '供应商准入审核',   20, 1, '关联供应商主数据', 1, NOW(), NOW()),
    (8103, 8001, 'FACTORY_AUDIT',  '实地验厂',         30, 1, '关联供应商主数据', 1, NOW(), NOW()),
    (8104, 8001, 'PACKAGE_REVIEW', '包装审核确认',     40, 0, NULL, 1, NOW(), NOW()),
    (8105, 8001, 'LISTING',        '上市',             50, 0, NULL, 1, NOW(), NOW()),
    (8106, 8001, 'POST_MARKET',    '上市后质量监控',   60, 0, NULL, 1, NOW(), NOW());

-- 默认节点的示例自定义字段（核心字段：状态/完成日期/负责人/结论/备注/附件 已由节点记录统一提供）
INSERT INTO qc_flow_node_field_def
    (id, node_def_id, field_code, field_name, field_type, options_json, required, sort, tenant_id, created_at, updated_at)
VALUES
    (8301, 8101, 'batch_no',    '上会批次',   'TEXT',       NULL,                                 0, 10, 1, NOW(), NOW()),
    (8302, 8101, 'vote_result', '表决情况',   'SELECT',     '["全票通过","多数通过","未通过"]',   0, 20, 1, NOW(), NOW()),
    (8311, 8102, 'doc_check',   '资质初审',   'SELECT',     '["合格","限期补正","不合格"]',       0, 10, 1, NOW(), NOW()),
    (8321, 8103, 'score',       '验厂得分',   'TEXT',       NULL,                                 0, 10, 1, NOW(), NOW()),
    (8322, 8103, 'issue_count', '整改项数量', 'TEXT',       NULL,                                 0, 20, 1, NOW(), NOW()),
    (8331, 8104, 'pkg_version', '包装版本',   'TEXT',       NULL,                                 0, 10, 1, NOW(), NOW()),
    (8332, 8104, 'label_ok',    '标签合规',   'SELECT',     '["合规","需整改"]',                  0, 20, 1, NOW(), NOW()),
    (8341, 8105, 'first_batch', '首批批号',   'TEXT',       NULL,                                 0, 10, 1, NOW(), NOW()),
    (8342, 8105, 'channel',     '上市渠道',   'SELECT',     '["线上","线下","全渠道"]',           0, 20, 1, NOW(), NOW()),
    (8351, 8106, 'period',      '监控周期',   'SELECT',     '["月度","季度"]',                    0, 10, 1, NOW(), NOW());

-- =====================================================================
-- 预置：资料项示例清单（管理员可在“流程模板配置”页继续维护）
-- =====================================================================
INSERT INTO qc_material_item (id, item_code, item_name, sort, is_preset, status, tenant_id, created_at, updated_at) VALUES
(8201, 'PRE_SAMPLE',       '产前样品',       10, 1, 1, 1, NOW(), NOW()),
(8202, 'STD_SAMPLE',       '标准样品',       20, 1, 1, 1, NOW(), NOW()),
(8203, 'TYPE_TEST_REPORT', '型式检验报告',   30, 1, 1, 1, NOW(), NOW()),
(8204, 'APPROVAL_CERT',    '批准文号/备案凭证', 40, 1, 1, 1, NOW(), NOW()),
(8205, 'PACKAGE_FINAL',    '包装确认稿',     50, 1, 1, 1, NOW(), NOW()),
(8206, 'SUPPLIER_DOC',     '供应商资质文件', 60, 1, 1, 1, NOW(), NOW()),
(8207, 'FACTORY_REPORT',   '实地验厂报告',   70, 1, 1, 1, NOW(), NOW()),
(8208, 'EXT_TEST_REPORT',  '第三方外检报告', 80, 1, 1, 1, NOW(), NOW());
