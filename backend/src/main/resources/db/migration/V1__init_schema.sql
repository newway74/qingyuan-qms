-- =====================================================================
-- 清源 QMS 初始化 schema（36 张表：10 系统表 + 26 业务表）
-- MySQL 8 / utf8mb4 / InnoDB
-- 规则：
--   业务表带 tenant_id / created_* / updated_* / deleted(逻辑删)
--   关键单据带 lock_version 乐观锁
--   审计/登录/签名/附件/待办/预警/审批时间线 为只增表，无 deleted/updated
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 组织
-- ---------------------------------------------------------------------
CREATE TABLE sys_dept (
    id              BIGINT       NOT NULL COMMENT '主键(雪花)',
    parent_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门，0=根',
    dept_name       VARCHAR(100) NOT NULL COMMENT '部门名称',
    sort            INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0停用',
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

-- ---------------------------------------------------------------------
-- 2. 用户
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGINT       NOT NULL,
    dept_id         BIGINT       NULL,
    username        VARCHAR(64)  NOT NULL COMMENT '登录名',
    real_name       VARCHAR(64)  NOT NULL COMMENT '姓名',
    password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    phone           VARCHAR(32)  NULL,
    email           VARCHAR(128) NULL,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用0停用',
    last_login_at   DATETIME     NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tenant_username (tenant_id, username),
    KEY idx_user_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- ---------------------------------------------------------------------
-- 3. 角色
-- ---------------------------------------------------------------------
CREATE TABLE sys_role (
    id              BIGINT       NOT NULL,
    code            VARCHAR(64)  NOT NULL COMMENT 'ADMIN/SAMPLER/INSPECTOR/REVIEWER/QA_MANAGER',
    name            VARCHAR(64)  NOT NULL,
    data_scope      VARCHAR(20)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL/CATEGORY/BRAND/DEPT',
    status          TINYINT      NOT NULL DEFAULT 1,
    remark          VARCHAR(255) NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- ---------------------------------------------------------------------
-- 4. 权限（菜单/按钮/接口）
-- ---------------------------------------------------------------------
CREATE TABLE sys_permission (
    id              BIGINT       NOT NULL,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    perm_code       VARCHAR(100) NULL COMMENT '权限码，如 master:category:list',
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(10)  NOT NULL COMMENT 'MENU/BUTTON/API',
    path            VARCHAR(200) NULL COMMENT '前端路由',
    icon            VARCHAR(64)  NULL,
    sort            INT          NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_perm_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

-- 5. 用户-角色
CREATE TABLE sys_user_role (
    user_id         BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_ur_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 6. 角色-权限
CREATE TABLE sys_role_permission (
    role_id         BIGINT       NOT NULL,
    permission_id   BIGINT       NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_rp_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- 7. 字典类型
CREATE TABLE sys_dict_type (
    id              BIGINT       NOT NULL,
    type_code       VARCHAR(64)  NOT NULL,
    type_name       VARCHAR(100) NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 1,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

-- 8. 字典项
CREATE TABLE sys_dict_item (
    id              BIGINT       NOT NULL,
    type_code       VARCHAR(64)  NOT NULL,
    item_value      VARCHAR(100) NOT NULL,
    item_label      VARCHAR(200) NOT NULL,
    sort            INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1,
    extra           VARCHAR(500) NULL COMMENT '扩展配置(JSON 字符串)',
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_dict_item_type (type_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项';

-- 9. 审计日志（只增）
CREATE TABLE sys_audit_log (
    id              BIGINT       NOT NULL,
    trace_id        VARCHAR(64)  NULL,
    user_id         BIGINT       NULL,
    username        VARCHAR(64)  NULL,
    module          VARCHAR(64)  NOT NULL COMMENT '模块',
    action          VARCHAR(32)  NOT NULL COMMENT 'CREATE/UPDATE/DELETE_LOGIC/EXPORT 等',
    biz_type        VARCHAR(64)  NULL,
    biz_id          BIGINT       NULL,
    before_value    JSON         NULL COMMENT '变更前值',
    after_value     JSON         NULL COMMENT '变更后值',
    ip              VARCHAR(64)  NULL,
    user_agent      VARCHAR(500) NULL,
    result          TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功0失败',
    error_msg       VARCHAR(1000) NULL,
    cost_ms         BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_biz (biz_type, biz_id),
    KEY idx_audit_user (username),
    KEY idx_audit_time (created_at),
    KEY idx_audit_module (module, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志(只增)';

-- 10. 登录日志（只增）
CREATE TABLE sys_login_log (
    id              BIGINT       NOT NULL,
    username        VARCHAR(64)  NULL,
    login_type      VARCHAR(16)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/SSO',
    success         TINYINT      NOT NULL DEFAULT 1,
    ip              VARCHAR(64)  NULL,
    user_agent      VARCHAR(500) NULL,
    fail_reason     VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_user (username),
    KEY idx_login_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志(只增)';

-- ---------------------------------------------------------------------
-- 11. 品类（树形）
-- ---------------------------------------------------------------------
CREATE TABLE qc_category (
    id              BIGINT       NOT NULL,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    sort            INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (tenant_id, code),
    KEY idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品类';

-- ---------------------------------------------------------------------
-- 12. 供应商
-- ---------------------------------------------------------------------
CREATE TABLE qc_supplier (
    id                  BIGINT       NOT NULL,
    supplier_code       VARCHAR(50)  NOT NULL,
    supplier_name       VARCHAR(150) NOT NULL,
    contact             VARCHAR(64)  NULL,
    phone               VARCHAR(32)  NULL,
    address             VARCHAR(255) NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'QUALIFIED' COMMENT 'QUALIFIED合格/CONTROLLED受控/DISABLED禁用',
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    lock_version        INT          NOT NULL DEFAULT 0,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (tenant_id, supplier_code),
    KEY idx_supplier_name (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商';

-- 13. 供应商证照
CREATE TABLE qc_supplier_license (
    id                  BIGINT       NOT NULL,
    supplier_id         BIGINT       NOT NULL,
    license_type        VARCHAR(50)  NOT NULL COMMENT '营业执照/生产许可/经营许可',
    cert_no             VARCHAR(100) NULL,
    valid_from          DATE         NULL,
    valid_to            DATE         NULL COMMENT '到期日，预警依据',
    file_attachment_id  BIGINT       NULL,
    status              TINYINT      NOT NULL DEFAULT 1,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_license_supplier (supplier_id),
    KEY idx_license_validto (valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商证照';

-- ---------------------------------------------------------------------
-- 14. 产品 SPU
-- ---------------------------------------------------------------------
CREATE TABLE qc_product (
    id                          BIGINT       NOT NULL,
    spu_code                    VARCHAR(50)  NOT NULL,
    product_name                VARCHAR(200) NOT NULL,
    category_id                 BIGINT       NOT NULL,
    brand                       VARCHAR(100) NULL,
    supplier_id                 BIGINT       NULL,
    execution_standard          VARCHAR(100) NULL COMMENT '执行标准号',
    storage_condition           VARCHAR(200) NULL COMMENT '储存条件',
    shelf_life_days             INT          NULL COMMENT '保质期天数',
    ext_inspection_required     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否需第三方外检',
    quality_status              VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/CONTROLLED/FROZEN/DISABLED',
    tenant_id                   BIGINT       NOT NULL DEFAULT 1,
    lock_version                INT          NOT NULL DEFAULT 0,
    created_by                  BIGINT       NULL,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                  BIGINT       NULL,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_spu (tenant_id, spu_code),
    KEY idx_product_category (category_id),
    KEY idx_product_supplier (supplier_id),
    KEY idx_product_quality (quality_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品档案 SPU';

-- 15. 产品 SKU
CREATE TABLE qc_product_sku (
    id                  BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    sku_code            VARCHAR(50)  NOT NULL,
    spec                VARCHAR(150) NULL COMMENT '规格',
    package_form        VARCHAR(30)  NULL COMMENT '袋装/罐装/盒装',
    net_content         DECIMAL(12,2) NULL COMMENT '标示净含量',
    net_content_unit    VARCHAR(10)  NULL DEFAULT 'g',
    barcode             VARCHAR(64)  NULL,
    status              TINYINT      NOT NULL DEFAULT 1,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (tenant_id, sku_code),
    KEY idx_sku_product (product_id),
    KEY idx_sku_barcode (barcode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品 SKU';

-- 16. 产品资质
CREATE TABLE qc_product_license (
    id                  BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    license_type        VARCHAR(50)  NOT NULL,
    cert_no             VARCHAR(100) NULL,
    valid_to            DATE         NULL,
    file_attachment_id  BIGINT       NULL,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plicense_product (product_id),
    KEY idx_plicense_validto (valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品资质';

-- 17. 供应商质量评级（周期快照）
CREATE TABLE qc_supplier_quality (
    id                  BIGINT       NOT NULL,
    supplier_id         BIGINT       NOT NULL,
    period              CHAR(6)      NOT NULL COMMENT '评级周期 yyyyMM',
    batch_count         INT          NOT NULL DEFAULT 0,
    pass_rate           DECIMAL(6,2) NULL COMMENT '合格率%',
    defect_count        INT          NOT NULL DEFAULT 0,
    score               DECIMAL(6,2) NULL,
    grade               CHAR(1)      NULL COMMENT 'A/B/C/D',
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_period (tenant_id, supplier_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商质量评级';

-- ---------------------------------------------------------------------
-- 18. 检验标准模板（一行一版本）
-- ---------------------------------------------------------------------
CREATE TABLE qc_standard_template (
    id                  BIGINT       NOT NULL,
    template_code       VARCHAR(50)  NOT NULL COMMENT '模板业务编码(跨版本相同)',
    template_name       VARCHAR(200) NOT NULL,
    category_id         BIGINT       NOT NULL,
    package_form        VARCHAR(30)  NULL COMMENT '适用包装形态，NULL=全部',
    version             INT          NOT NULL DEFAULT 1 COMMENT '业务版本号',
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    max_a_fail          INT          NOT NULL DEFAULT 0 COMMENT 'A类允许不合格数',
    max_b_fail          INT          NOT NULL DEFAULT 0 COMMENT 'B类允许不合格数',
    max_c_fail          INT          NOT NULL DEFAULT 0 COMMENT 'C类允许不合格数',
    concession_allowed  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许让步接收',
    remark              VARCHAR(500) NULL,
    published_by        BIGINT       NULL,
    published_at        DATETIME     NULL,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    lock_version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tpl_code_ver (template_code, version),
    KEY idx_tpl_category (category_id),
    KEY idx_tpl_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验标准模板(版本化)';

-- 19. 检验项
CREATE TABLE qc_standard_item (
    id                  BIGINT       NOT NULL,
    template_id         BIGINT       NOT NULL,
    group_code          VARCHAR(30)  NOT NULL COMMENT 'SENSORY/PACKAGE_LABEL/NET_CONTENT/PHYSICO/MICRO/CERT_DOC',
    item_name           VARCHAR(200) NOT NULL,
    inspect_method      VARCHAR(200) NULL COMMENT '检验方法',
    result_type         VARCHAR(20)  NOT NULL COMMENT 'QUALITATIVE/QUANTITATIVE/DOCUMENT',
    defect_level        CHAR(1)      NOT NULL DEFAULT 'C' COMMENT 'A/B/C',
    veto_flag           TINYINT      NOT NULL DEFAULT 0 COMMENT '一票否决',
    required_flag       TINYINT      NOT NULL DEFAULT 1 COMMENT '必检',
    min_value           DECIMAL(14,4) NULL,
    max_value           DECIMAL(14,4) NULL,
    nominal_value       DECIMAL(14,4) NULL COMMENT '标示值(净含量)',
    unit                VARCHAR(20)  NULL,
    tolerance_rule      VARCHAR(50)  NULL COMMENT '短缺量规则编码 如 JJF1070',
    judge_config        JSON         NULL COMMENT '扩展判定规则',
    sort                INT          NOT NULL DEFAULT 0,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_item_template (template_id),
    KEY idx_item_group (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验项';

-- ---------------------------------------------------------------------
-- 20. 品控流程定义（一行一版本）
-- ---------------------------------------------------------------------
CREATE TABLE qc_process_def (
    id                  BIGINT       NOT NULL,
    process_code        VARCHAR(50)  NOT NULL,
    process_name        VARCHAR(200) NOT NULL,
    version             INT          NOT NULL DEFAULT 1,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remark              VARCHAR(500) NULL,
    published_by        BIGINT       NULL,
    published_at        DATETIME     NULL,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    lock_version        INT          NOT NULL DEFAULT 0,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pdef_code_ver (process_code, version),
    KEY idx_pdef_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品控流程定义(版本化)';

-- 21. 流程节点
CREATE TABLE qc_process_node (
    id                  BIGINT       NOT NULL,
    process_def_id      BIGINT       NOT NULL,
    node_code           VARCHAR(50)  NOT NULL COMMENT 'SAMPLING/RECEIVE/ASSIGN/INSPECT/REVIEW/JUDGE/DEFECT/RECHECK/ARCHIVE',
    node_name           VARCHAR(100) NOT NULL,
    responsible_role    VARCHAR(64)  NOT NULL,
    sla_hours           INT          NOT NULL DEFAULT 0 COMMENT 'SLA 自然小时',
    calendar_type       VARCHAR(20)  NOT NULL DEFAULT 'NATURAL' COMMENT 'NATURAL自然时间/WORKDAY工作日历(预留)',
    required_fields     JSON         NULL,
    transition_rules    JSON         NULL,
    sort                INT          NOT NULL DEFAULT 0,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pnode_def (process_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点';

-- 22. 流程变更记录（只增）
CREATE TABLE qc_process_change_log (
    id                  BIGINT       NOT NULL,
    process_def_id      BIGINT       NOT NULL,
    process_code        VARCHAR(50)  NOT NULL,
    from_version        INT          NULL,
    to_version          INT          NOT NULL,
    change_diff         JSON         NULL,
    changed_by          BIGINT       NULL,
    changed_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_pchange_def (process_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程发布变更记录(只增)';

-- ---------------------------------------------------------------------
-- 23. 抽样单
-- ---------------------------------------------------------------------
CREATE TABLE qc_sampling (
    id                  BIGINT       NOT NULL,
    sampling_no         VARCHAR(40)  NOT NULL COMMENT 'CY编号',
    source              VARCHAR(30)  NOT NULL COMMENT 'NEW_ADMISSION/INCOMING_BATCH/PERIODIC/COMPLAINT/FLYING',
    sku_id              BIGINT       NOT NULL,
    batch_no            VARCHAR(64)  NOT NULL COMMENT '批号',
    production_date     DATE         NULL,
    expiry_date         DATE         NULL COMMENT '保质期至',
    storage_condition   VARCHAR(200) NULL,
    sample_quantity     DECIMAL(12,2) NULL,
    quantity_unit       VARCHAR(10)  NULL DEFAULT '件',
    sampling_location   VARCHAR(200) NULL,
    sampler_id          BIGINT       NULL,
    sampled_at          DATETIME     NULL COMMENT '抽样时间(业务时间)',
    remark              VARCHAR(500) NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    process_def_id      BIGINT       NULL COMMENT '流程版本快照',
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    lock_version        INT          NOT NULL DEFAULT 0,
    created_by          BIGINT       NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sampling_no (sampling_no),
    KEY idx_sampling_sku (sku_id),
    KEY idx_sampling_batch (batch_no),
    KEY idx_sampling_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽样单';

-- 24. 样品
CREATE TABLE qc_sample (
    id                      BIGINT       NOT NULL,
    sample_no               VARCHAR(40)  NOT NULL COMMENT 'YP条码号',
    sampling_id             BIGINT       NOT NULL,
    sku_id                  BIGINT       NOT NULL,
    sample_type             VARCHAR(20)  NOT NULL COMMENT 'INSPECTION/RETAIN/BACKUP',
    batch_no                VARCHAR(64)  NOT NULL,
    production_date         DATE         NULL,
    expiry_date             DATE         NULL,
    package_batch_check     VARCHAR(20)  NULL COMMENT '喷码核对 MATCH/MISMATCH',
    package_batch_note      VARCHAR(500) NULL COMMENT '喷码差异说明',
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING_RECEIVE',
    received_at             DATETIME     NULL,
    receiver_id             BIGINT       NULL,
    retain_flag             TINYINT      NOT NULL DEFAULT 0,
    retain_location         VARCHAR(100) NULL,
    retain_until            DATE         NULL,
    dispose_type            VARCHAR(30)  NULL COMMENT 'DESTROY销毁/RETURN退还',
    dispose_remark          VARCHAR(500) NULL,
    disposed_at             DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sample_no (sample_no),
    KEY idx_sample_sampling (sampling_id),
    KEY idx_sample_sku (sku_id),
    KEY idx_sample_batch (batch_no),
    KEY idx_sample_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样品';

-- 25. 批次台账（轻量效期）
CREATE TABLE qc_batch (
    id                      BIGINT       NOT NULL,
    sku_id                  BIGINT       NOT NULL,
    batch_no                VARCHAR(64)  NOT NULL,
    production_date         DATE         NULL,
    expiry_date             DATE         NULL,
    storage_condition       VARCHAR(200) NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/NEAR_EXPIRY/EXPIRED',
    last_check_at           DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch (tenant_id, sku_id, batch_no),
    KEY idx_batch_expiry (expiry_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次台账';

-- ---------------------------------------------------------------------
-- 26. 检验任务
-- ---------------------------------------------------------------------
CREATE TABLE qc_inspection_task (
    id                      BIGINT       NOT NULL,
    task_no                 VARCHAR(40)  NOT NULL COMMENT 'JC编号',
    sample_id               BIGINT       NOT NULL,
    template_id             BIGINT       NOT NULL COMMENT '模板具体版本主键',
    process_def_id          BIGINT       NULL,
    inspector_id            BIGINT       NULL,
    reviewer_id             BIGINT       NULL,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING_ASSIGN',
    sla_deadline            DATETIME     NULL,
    assigned_at             DATETIME     NULL,
    started_at              DATETIME     NULL,
    submitted_at            DATETIME     NULL,
    reviewed_at             DATETIME     NULL,
    recheck_parent_id       BIGINT       NULL COMMENT '复检原任务',
    recheck_reason          VARCHAR(500) NULL,
    round_no                INT          NOT NULL DEFAULT 1 COMMENT '检验轮次',
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_task_sample (sample_id),
    KEY idx_task_inspector (inspector_id, status),
    KEY idx_task_reviewer (reviewer_id, status),
    KEY idx_task_status (status),
    KEY idx_task_sla (sla_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验任务';

-- 27. 检验结果（逐项）
CREATE TABLE qc_inspection_result (
    id                      BIGINT       NOT NULL,
    task_id                 BIGINT       NOT NULL,
    item_id                 BIGINT       NOT NULL COMMENT '检验项版本快照id',
    group_code              VARCHAR(30)  NOT NULL,
    item_name               VARCHAR(200) NOT NULL,
    result_type             VARCHAR(20)  NOT NULL,
    qualitative_value       VARCHAR(20)  NULL COMMENT 'PASS/FAIL/NA',
    quantitative_value      DECIMAL(14,4) NULL,
    unit                    VARCHAR(20)  NULL,
    doc_attachment_id       BIGINT       NULL,
    auto_judgement          VARCHAR(20)  NULL COMMENT 'PASS/FAIL/NONE',
    final_judgement         VARCHAR(20)  NULL COMMENT '复核后最终判定',
    judge_snapshot          JSON         NULL COMMENT '规则/阈值快照',
    remark                  VARCHAR(500) NULL,
    entered_by              BIGINT       NULL,
    entered_at              DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_result_task_item (task_id, item_id),
    KEY idx_result_task (task_id),
    KEY idx_result_judge (auto_judgement)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验结果';

-- 28. 检验报告
CREATE TABLE qc_inspection_report (
    id                      BIGINT       NOT NULL,
    task_id                 BIGINT       NOT NULL,
    report_no               VARCHAR(40)  NOT NULL COMMENT 'BG编号',
    conclusion              VARCHAR(30)  NOT NULL COMMENT 'QUALIFIED/UNQUALIFIED/CONCESSION/PENDING_RECHECK',
    a_fail_count            INT          NOT NULL DEFAULT 0,
    b_fail_count            INT          NOT NULL DEFAULT 0,
    c_fail_count            INT          NOT NULL DEFAULT 0,
    defect_summary          JSON         NULL,
    inspector_sign_hash     VARCHAR(128) NULL,
    inspector_signed_at     DATETIME     NULL,
    reviewer_sign_hash      VARCHAR(128) NULL,
    reviewer_signed_at      DATETIME     NULL,
    pdf_attachment_id       BIGINT       NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SIGNED/ISSUED',
    issued_at               DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_no (report_no),
    KEY idx_report_task (task_id),
    KEY idx_report_conclusion (conclusion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验报告';

-- 29. 电子签名记录（只增）
CREATE TABLE qc_signature_record (
    id                      BIGINT       NOT NULL,
    biz_type                 VARCHAR(30)  NOT NULL COMMENT 'TASK_REVIEW/REPORT/DEFECT_APPROVAL',
    biz_id                  BIGINT       NOT NULL,
    user_id                 BIGINT       NOT NULL,
    username                VARCHAR(64)  NULL,
    sign_purpose            VARCHAR(100) NULL,
    snapshot_hash           VARCHAR(128) NULL COMMENT '单据快照SHA256',
    ip                      VARCHAR(64)  NULL,
    user_agent              VARCHAR(500) NULL,
    signed_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sign_biz (biz_type, biz_id),
    KEY idx_sign_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子签名记录(只增)';

-- 30. 附件（只增记录）
CREATE TABLE qc_attachment (
    id                      BIGINT       NOT NULL,
    biz_type                VARCHAR(50)  NOT NULL,
    biz_id                  BIGINT       NOT NULL DEFAULT 0,
    file_name               VARCHAR(255) NOT NULL,
    file_ext                VARCHAR(20)  NULL,
    file_size               BIGINT       NOT NULL DEFAULT 0,
    content_type            VARCHAR(100) NULL,
    storage_type            VARCHAR(20)  NOT NULL DEFAULT 'MINIO',
    bucket                  VARCHAR(100) NULL,
    object_key              VARCHAR(500) NOT NULL,
    sha256                  VARCHAR(64)  NULL,
    uploaded_by             BIGINT       NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_attach_biz (biz_type, biz_id),
    KEY idx_attach_sha (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件元数据(只增)';

-- ---------------------------------------------------------------------
-- 31. 不合格处置单
-- ---------------------------------------------------------------------
CREATE TABLE qc_defect_case (
    id                      BIGINT       NOT NULL,
    case_no                 VARCHAR(40)  NOT NULL COMMENT 'BH编号',
    report_id               BIGINT       NULL,
    task_id                 BIGINT       NULL,
    sample_id               BIGINT       NULL,
    max_severity            CHAR(1)      NULL COMMENT 'A/B/C',
    disposition             VARCHAR(30)  NULL COMMENT 'RETURN/OFF_SHELF/DESTROY/EXCHANGE/RECTIFY/CONCESSION',
    root_cause              VARCHAR(1000) NULL,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING_REVIEW',
    owner_id                BIGINT       NULL,
    closed_at               DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_case_no (case_no),
    KEY idx_case_status (status),
    KEY idx_case_supplier (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不合格处置单';

-- 32. 缺陷明细
CREATE TABLE qc_defect_item (
    id                      BIGINT       NOT NULL,
    case_id                 BIGINT       NOT NULL,
    result_id               BIGINT       NULL,
    defect_level            CHAR(1)      NOT NULL,
    item_name               VARCHAR(200) NULL,
    fail_desc               VARCHAR(1000) NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ditem_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不合格缺陷明细';

-- 33. 处置审批时间线（只增）
CREATE TABLE qc_defect_approval (
    id                      BIGINT       NOT NULL,
    case_id                 BIGINT       NOT NULL,
    node                    VARCHAR(30)  NOT NULL COMMENT 'REVIEW/APPROVAL/DISPOSE/RECTIFY/RECHECK/CLOSE',
    action                  VARCHAR(30)  NOT NULL COMMENT 'SUBMIT/PASS/REJECT/CLOSE',
    comment                 VARCHAR(1000) NULL,
    before_snapshot         JSON         NULL,
    after_snapshot          JSON         NULL,
    attachment_ids          VARCHAR(500) NULL,
    operator_id             BIGINT       NULL,
    operator_name           VARCHAR(64)  NULL,
    operated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_dapproval_case (case_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不合格审批时间线(只增)';

-- 34. 供应商整改单
CREATE TABLE qc_supplier_rectification (
    id                      BIGINT       NOT NULL,
    rectify_no              VARCHAR(40)  NOT NULL COMMENT 'ZG编号',
    defect_case_id          BIGINT       NOT NULL,
    supplier_id             BIGINT       NOT NULL,
    issue_desc              TEXT         NULL,
    corrective_action       TEXT         NULL,
    plan_finish_date        DATE         NULL,
    actual_finish_date      DATE         NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED/REPLIED/VERIFYING/PASSED/FAILED',
    recheck_task_id         BIGINT       NULL,
    verifier_id             BIGINT       NULL,
    verified_at             DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    lock_version            INT          NOT NULL DEFAULT 0,
    created_by              BIGINT       NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT       NULL,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rectify_no (rectify_no),
    KEY idx_rect_case (defect_case_id),
    KEY idx_rect_supplier (supplier_id),
    KEY idx_rect_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商整改单';

-- ---------------------------------------------------------------------
-- 35. 统一待办
-- ---------------------------------------------------------------------
CREATE TABLE qc_todo (
    id                      BIGINT       NOT NULL,
    user_id                 BIGINT       NULL COMMENT '为空=角色待办',
    role_code               VARCHAR(64)  NULL,
    todo_type               VARCHAR(30)  NOT NULL COMMENT 'RECEIVE/INSPECT/REVIEW/DEFECT/APPROVAL/RECT/ALERT',
    biz_type                VARCHAR(50)  NOT NULL,
    biz_id                  BIGINT       NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    biz_no                  VARCHAR(40)  NULL,
    priority                INT          NOT NULL DEFAULT 0,
    status                  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待办1已处理',
    deadline                DATETIME     NULL,
    handled_at              DATETIME     NULL,
    handler_id              BIGINT       NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_todo_user (user_id, status, priority),
    KEY idx_todo_role (role_code, status),
    KEY idx_todo_biz (biz_type, biz_id),
    KEY idx_todo_deadline (deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一待办';

-- 36. 预警
CREATE TABLE qc_alert (
    id                      BIGINT       NOT NULL,
    alert_type              VARCHAR(30)  NOT NULL COMMENT 'SLA_WARNING/SLA_OVERDUE/RETAIN_EXPIRE/LICENSE_EXPIRE/NEAR_EXPIRY/BATCH_EXPIRED',
    biz_type                VARCHAR(50)  NOT NULL,
    biz_id                  BIGINT       NOT NULL,
    target_role             VARCHAR(64)  NULL,
    target_user_id          BIGINT       NULL,
    message                 VARCHAR(500) NOT NULL,
    level                   TINYINT      NOT NULL DEFAULT 1 COMMENT '1提示2警告3严重',
    status                  TINYINT      NOT NULL DEFAULT 0 COMMENT '0未处理1已处理',
    dedup_key               VARCHAR(200) NOT NULL COMMENT '防重复键',
    triggered_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at              DATETIME     NULL,
    handler_id              BIGINT       NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_dedup (dedup_key),
    KEY idx_alert_type (alert_type, status),
    KEY idx_alert_target (target_role, target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量预警';
