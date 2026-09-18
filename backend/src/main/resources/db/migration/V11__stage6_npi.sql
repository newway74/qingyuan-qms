-- =====================================================================
-- V11: 阶段6 新品引入（NPI）主线
--   业务链路：新品会立项 → 高/中/低标准起草（国标依据+竞品对标）
--             → 采购/老板评审 → 选档寻源 → 多家送样评估对比 → 供应商定点
--             → 实地验厂 → 量产 → 第三方/国家检测送检 → 上市放行
-- 标准仍复用 qc_standard_template（一行一版本），新增档位/依据/项目关联字段。
-- =====================================================================

-- ---------- 0. 检验标准模板增强：档位 / 国标依据 / 市面竞品对标 / 来源项目 ----------
ALTER TABLE qc_standard_template
    ADD COLUMN grade            VARCHAR(8)    NULL COMMENT '档位 HIGH高档/MID中档/LOW低档' AFTER package_form,
    ADD COLUMN npi_project_id   BIGINT        NULL COMMENT '来源新品引入项目' AFTER grade,
    ADD COLUMN regulation_basis VARCHAR(500)  NULL COMMENT '国标/法规依据（GB/药典/NMPA注册要求等）' AFTER npi_project_id,
    ADD COLUMN market_benchmark VARCHAR(1000) NULL COMMENT '市面竞品对标说明' AFTER regulation_basis,
    ADD KEY idx_tpl_npi_project (npi_project_id),
    ADD KEY idx_tpl_grade (grade);

-- ---------- 1. 标准评审记录（采购会签 / 老板批准，只增） ----------
CREATE TABLE qc_std_review (
    id                      BIGINT       NOT NULL,
    template_id             BIGINT       NOT NULL,
    node                    VARCHAR(20)  NOT NULL COMMENT 'SUBMIT提交/PROCUREMENT采购会签/BOSS老板批准',
    action                  VARCHAR(20)  NOT NULL COMMENT 'SUBMIT/PASS/REJECT/COMMENT',
    comment                 VARCHAR(1000) NULL,
    operator_id             BIGINT       NULL,
    operator_name           VARCHAR(64)  NULL,
    operated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_review_template (template_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准评审记录(只增)';

-- ---------- 2. 新品引入项目 ----------
CREATE TABLE qc_npi_project (
    id                      BIGINT       NOT NULL,
    project_no              VARCHAR(40)  NOT NULL COMMENT 'NP编号',
    project_name            VARCHAR(200) NOT NULL,
    category_id             BIGINT       NOT NULL COMMENT '拟引入品类',
    brand                   VARCHAR(100) NULL COMMENT '拟引入品牌',
    background              VARCHAR(1000) NULL COMMENT '立项背景/新品会结论',
    meeting_at              DATETIME     NULL COMMENT '新品会时间',
    attendees               VARCHAR(500) NULL COMMENT '参会人（品控/采购/老板）',
    initiator_id            BIGINT       NULL COMMENT '立项发起人',
    target_listing_date     DATE         NULL COMMENT '计划上市日期',
    status                  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT立项/STD_REVIEW标准评审中/SOURCING寻源送样中/AUDIT验厂中/PRODUCING量产中/EXT_TEST外检中/LISTING_REVIEW待上市放行/LISTED已上市/TERMINATED已终止',
    target_grade            VARCHAR(8)   NULL COMMENT '寻源选定档位 HIGH/MID/LOW',
    selected_template_id    BIGINT       NULL COMMENT '寻源选定标准版本',
    chosen_supplier_id      BIGINT       NULL COMMENT '定点供应商',
    product_id              BIGINT       NULL COMMENT '上市后关联产品档案',
    std_submitted_at        DATETIME     NULL,
    std_approved_at         DATETIME     NULL COMMENT '标准评审通过（进入寻源）',
    supplier_fixed_at       DATETIME     NULL,
    audit_passed_at         DATETIME     NULL,
    ext_test_passed_at      DATETIME     NULL,
    listed_at               DATETIME     NULL,
    terminate_reason        VARCHAR(500) NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_npi_no (project_no),
    KEY idx_npi_category (category_id),
    KEY idx_npi_status (status),
    KEY idx_npi_supplier (chosen_supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新品引入项目';

-- ---------- 3. 项目阶段时间线（只增） ----------
CREATE TABLE qc_npi_timeline (
    id                      BIGINT       NOT NULL,
    project_id              BIGINT       NOT NULL,
    stage                   VARCHAR(30)  NOT NULL COMMENT '阶段/事件编码',
    action                  VARCHAR(30)  NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    comment                 VARCHAR(1000) NULL,
    operator_id             BIGINT       NULL,
    operator_name           VARCHAR(64)  NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_npi_timeline_project (project_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新品项目阶段时间线(只增)';

-- ---------- 4. 送样评估单（多家供应商横向对比） ----------
CREATE TABLE qc_npi_eval (
    id                      BIGINT       NOT NULL,
    eval_no                 VARCHAR(40)  NOT NULL COMMENT 'PG编号',
    project_id              BIGINT       NOT NULL,
    supplier_id             BIGINT       NOT NULL,
    round_no                INT          NOT NULL DEFAULT 1 COMMENT '送样轮次',
    sample_desc             VARCHAR(500) NULL COMMENT '样品描述（规格/批号/数量）',
    received_at             DATETIME     NULL,
    quality_conclusion      VARCHAR(20)  NULL COMMENT '对照标准检验结论 PENDING/QUALIFIED/UNQUALIFIED',
    total_score             DECIMAL(6,2) NULL COMMENT '加权总分0-100',
    rank_no                 INT          NULL COMMENT '本轮排名',
    selected_flag           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否定点 1是',
    report_attachment_id    BIGINT       NULL,
    remark                  VARCHAR(500) NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED',
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_no (eval_no),
    KEY idx_eval_project (project_id),
    KEY idx_eval_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新品送样评估单';

-- ---------- 5. 送样评估评分项（维度） ----------
CREATE TABLE qc_npi_eval_item (
    id                      BIGINT       NOT NULL,
    eval_id                 BIGINT       NOT NULL,
    dimension_code          VARCHAR(30)  NOT NULL COMMENT 'QUALITY质量符合/PACKAGE包装标签/PRICE价格/DELIVERY交期/SERVICE服务',
    dimension_name          VARCHAR(100) NOT NULL,
    score                   DECIMAL(6,2) NULL COMMENT '得分0-100',
    weight                  DECIMAL(5,2) NOT NULL DEFAULT 20 COMMENT '权重(合计100)',
    note                    VARCHAR(500) NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_eval_item_eval (eval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样评估评分项';

-- ---------- 6. 实地验厂单 ----------
CREATE TABLE qc_factory_audit (
    id                      BIGINT       NOT NULL,
    audit_no                VARCHAR(40)  NOT NULL COMMENT 'YC编号',
    project_id              BIGINT       NOT NULL,
    supplier_id             BIGINT       NOT NULL,
    parent_audit_id         BIGINT       NULL COMMENT '整改复审关联初审单',
    audit_type              VARCHAR(20)  NOT NULL DEFAULT 'INITIAL' COMMENT 'INITIAL初审/FOLLOW_UP复审',
    planned_at              DATETIME     NULL,
    audited_at              DATETIME     NULL,
    leader_id               BIGINT       NULL,
    auditors                VARCHAR(500) NULL COMMENT '验厂组成员（姓名）',
    total_score             DECIMAL(6,2) NULL,
    conclusion              VARCHAR(20)  NULL COMMENT 'PASS合格/CONDITIONAL限期整改/FAIL不合格',
    rectify_requirement     VARCHAR(1000) NULL COMMENT '整改要求',
    rectify_deadline        DATE         NULL,
    report_attachment_id    BIGINT       NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/IN_PROGRESS/SUBMITTED/CONFIRMED',
    remark                  VARCHAR(500) NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_no (audit_no),
    KEY idx_audit_project (project_id),
    KEY idx_audit_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商实地验厂单';

-- ---------- 7. 验厂检查项 ----------
CREATE TABLE qc_factory_audit_item (
    id                      BIGINT       NOT NULL,
    audit_id                BIGINT       NOT NULL,
    category                VARCHAR(30)  NOT NULL COMMENT 'QUALIFICATION资质/PRODUCTION生产/WAREHOUSE仓储/COLD_CHAIN冷链/QUALITY_SYSTEM质量体系',
    item_name               VARCHAR(200) NOT NULL,
    requirement             VARCHAR(500) NULL,
    max_score               DECIMAL(6,2) NOT NULL DEFAULT 100,
    score                   DECIMAL(6,2) NULL,
    result                  VARCHAR(20)  NULL COMMENT 'COMPLIANT符合/MINOR一般不符合/MAJOR严重不符合/NA不适用',
    note                    VARCHAR(500) NULL,
    sort                    INT          NOT NULL DEFAULT 0,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_audit_item_audit (audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验厂检查项';

-- ---------- 8. 第三方/国家检测送检单 ----------
CREATE TABLE qc_external_test (
    id                      BIGINT       NOT NULL,
    test_no                 VARCHAR(40)  NOT NULL COMMENT 'WJ编号',
    project_id              BIGINT       NOT NULL,
    supplier_id             BIGINT       NOT NULL,
    product_name            VARCHAR(200) NULL COMMENT '送检样品名称（规格）',
    sample_desc             VARCHAR(500) NULL,
    lab_name                VARCHAR(200) NULL COMMENT '检测机构',
    lab_qualification       VARCHAR(30)  NULL COMMENT 'CMA/CNAS/NMPA/OTHER',
    test_items              VARCHAR(1000) NULL COMMENT '送检项目清单',
    sent_at                 DATETIME     NULL,
    report_no               VARCHAR(100) NULL,
    report_date             DATE         NULL,
    conclusion              VARCHAR(20)  NULL COMMENT 'PENDING检测中/PASS合格/FAIL不合格/PARTIAL部分不合格',
    report_attachment_id    BIGINT       NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/SENT已送检/REPORTED已出报告',
    remark                  VARCHAR(500) NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_no (test_no),
    KEY idx_extest_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方/国家检测送检单';

-- =====================================================================
-- 权限（1100 段：新品引入）
-- =====================================================================
INSERT INTO sys_permission (id, parent_id, perm_code, name, type, path, icon, sort, tenant_id) VALUES
(1101, 0,    'npi:project:list',    '新品引入',   'MENU', '/npi/projects',   'RocketOutlined', 15, 1),
(1102, 1101, 'npi:project:view',    '项目详情',   'BUTTON', NULL, NULL, 1, 1),
(1103, 1101, 'npi:project:create',  '新品立项',   'BUTTON', NULL, NULL, 2, 1),
(1104, 1101, 'npi:project:edit',    '项目编辑',   'BUTTON', NULL, NULL, 3, 1),
(1105, 1101, 'npi:project:stage',   '阶段推进',   'BUTTON', NULL, NULL, 4, 1),
(1106, 1101, 'npi:project:release', '上市放行',   'BUTTON', NULL, NULL, 5, 1),
(1111, 0,    'npi:audit:list',      '验厂管理',   'MENU', '/npi/audits',     'AuditOutlined', 16, 1),
(1112, 1111, 'npi:audit:edit',      '验厂登记评分','BUTTON', NULL, NULL, 1, 1),
(1121, 0,    'npi:exttest:list',    '外检送检',   'MENU', '/npi/ext-tests',  'SafetyCertificateOutlined', 17, 1),
(1122, 1121, 'npi:exttest:edit',    '送检登记',   'BUTTON', NULL, NULL, 1, 1),
(1131, 601,  'std:template:review', '标准评审签批','BUTTON', NULL, NULL, 5, 1);

-- ADMIN 通配 * 已覆盖；质量主管授予全部新品引入权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 5, id FROM sys_permission
WHERE perm_code LIKE 'npi:%' OR perm_code = 'std:template:review';

-- =====================================================================
-- 数据字典
-- =====================================================================
INSERT INTO sys_dict_type (id, type_code, type_name, status, tenant_id) VALUES
(16, 'npi_status',            '新品项目状态', 1, 1),
(17, 'std_grade',             '质量标准档位', 1, 1),
(18, 'ext_test_conclusion',   '外检结论',     1, 1),
(19, 'audit_conclusion',      '验厂结论',     1, 1),
(20, 'audit_item_result',     '验厂检查结果', 1, 1),
(21, 'eval_dimension',        '送样评估维度', 1, 1);

INSERT INTO sys_dict_item (id, type_code, item_value, item_label, sort, status, tenant_id) VALUES
(2001,'npi_status','DRAFT',           '立项',         1,1,1),
(2002,'npi_status','STD_REVIEW',      '标准评审中',   2,1,1),
(2003,'npi_status','SOURCING',        '寻源送样中',   3,1,1),
(2004,'npi_status','AUDIT',           '验厂中',       4,1,1),
(2005,'npi_status','PRODUCING',       '量产中',       5,1,1),
(2006,'npi_status','EXT_TEST',        '外检中',       6,1,1),
(2007,'npi_status','LISTING_REVIEW',  '待上市放行',   7,1,1),
(2008,'npi_status','LISTED',          '已上市',       8,1,1),
(2009,'npi_status','TERMINATED',      '已终止',       9,1,1),
(2011,'std_grade','HIGH',  '高档（企业定制/严于国标）', 1,1,1),
(2012,'std_grade','MID',   '中档（符合国标并加严）',   2,1,1),
(2013,'std_grade','LOW',   '低档（国标准入底线）',     3,1,1),
(2021,'ext_test_conclusion','PENDING','检测中',     1,1,1),
(2022,'ext_test_conclusion','PASS',   '合格',       2,1,1),
(2023,'ext_test_conclusion','FAIL',   '不合格',     3,1,1),
(2024,'ext_test_conclusion','PARTIAL','部分不合格', 4,1,1),
(2031,'audit_conclusion','PASS',        '合格',       1,1,1),
(2032,'audit_conclusion','CONDITIONAL', '限期整改',   2,1,1),
(2033,'audit_conclusion','FAIL',        '不合格',     3,1,1),
(2041,'audit_item_result','COMPLIANT','符合',       1,1,1),
(2042,'audit_item_result','MINOR',    '一般不符合', 2,1,1),
(2043,'audit_item_result','MAJOR',    '严重不符合', 3,1,1),
(2044,'audit_item_result','NA',       '不适用',     4,1,1),
(2051,'eval_dimension','QUALITY', '质量符合性', 1,1,1),
(2052,'eval_dimension','PACKAGE', '包装与标签', 2,1,1),
(2053,'eval_dimension','PRICE',   '价格竞争力', 3,1,1),
(2054,'eval_dimension','DELIVERY','交期保障',   4,1,1),
(2055,'eval_dimension','SERVICE', '服务配合',   5,1,1);
