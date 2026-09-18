-- =====================================================================
-- V7：阶段3 抽样收样 / 检验执行 / 复核判定 / 质检报告
-- 1) 补充阶段3所需列（撤销原因、保质期快照与差异、复核驳回原因、复核改判备注）
-- 2) JJF1070 净含量短缺量分档表（定量包装商品净含量计量检验规则）
-- 3) 批次台账演示数据（正常/近效期/过期，供阶段5预警演示）
-- =====================================================================

-- ---------- 列补充 ----------
ALTER TABLE qc_sampling
    ADD COLUMN cancel_reason             VARCHAR(500) NULL COMMENT '撤销原因（强制留痕）' AFTER remark,
    ADD COLUMN shelf_life_days_snapshot  INT          NULL COMMENT '提交时产品保质期天数快照' AFTER cancel_reason,
    ADD COLUMN expiry_diff_days          INT          NULL COMMENT '喷码保质期与标准保质期差异(天)' AFTER shelf_life_days_snapshot;

ALTER TABLE qc_inspection_task
    ADD COLUMN review_reject_reason VARCHAR(500) NULL COMMENT '复核驳回原因（留痕）' AFTER recheck_reason;

ALTER TABLE qc_inspection_result
    ADD COLUMN review_note VARCHAR(500) NULL COMMENT '复核改判说明（仅B/C类可改判）' AFTER final_judgement;

-- ---------- JJF1070 净含量允许短缺量分档（单位 g/ml） ----------
-- 区间：min_qty < 标示净含量 Q <= max_qty（min 开区间，max 闭区间；max_qty 为空表示上不封顶）
-- shortage_type: PERCENT=按标示值百分比 T；ABSOLUTE=绝对短缺量（g/ml）
-- 判定：实测净含量 >= 标示值 × (1 - T)，绝对量时 T 直接相减
CREATE TABLE qc_net_content_tolerance (
    id              BIGINT        NOT NULL,
    min_qty         DECIMAL(14,3) NOT NULL COMMENT '区间下限(不含)',
    max_qty         DECIMAL(14,3) NULL     COMMENT '区间上限(含), NULL=上不封顶',
    shortage_type   VARCHAR(10)   NOT NULL COMMENT 'PERCENT/ABSOLUTE',
    shortage_value  DECIMAL(10,4) NOT NULL COMMENT '百分比值(9表示9%)或绝对短缺量',
    unit_scope      VARCHAR(20)   NOT NULL DEFAULT 'G_ML',
    sort            INT           NOT NULL DEFAULT 0,
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    created_by      BIGINT        NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT        NULL,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tol_scope (unit_scope, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='JJF1070净含量允许短缺量分档';

INSERT INTO qc_net_content_tolerance
(id, min_qty, max_qty, shortage_type, shortage_value, unit_scope, sort, tenant_id, created_by) VALUES
(6001,     0.000,    50.000, 'PERCENT',  9.0000, 'G_ML', 1,  1, 1),
(6002,    50.000,   100.000, 'ABSOLUTE', 4.5000, 'G_ML', 2,  1, 1),
(6003,   100.000,   200.000, 'PERCENT',  4.5000, 'G_ML', 3,  1, 1),
(6004,   200.000,   300.000, 'ABSOLUTE', 9.0000, 'G_ML', 4,  1, 1),
(6005,   300.000,   500.000, 'PERCENT',  3.0000, 'G_ML', 5,  1, 1),
(6006,   500.000,  1000.000, 'ABSOLUTE', 15.0000,'G_ML', 6,  1, 1),
(6007,  1000.000, 10000.000, 'PERCENT',  1.5000, 'G_ML', 7,  1, 1),
(6008, 10000.000, 15000.000, 'ABSOLUTE', 150.0000,'G_ML', 8, 1, 1),
(6009, 15000.000, 50000.000, 'PERCENT',  1.0000, 'G_ML', 9,  1, 1);

-- ---------- 批次台账演示数据（日期相对当前生成，保证近效期/过期可演示） ----------
INSERT INTO qc_batch
(id, sku_id, batch_no, production_date, expiry_date, storage_condition, status, last_check_at, tenant_id, created_by, created_at, updated_by, updated_at, deleted) VALUES
(5001, 3001, 'B2026060101', DATE_SUB(CURDATE(), INTERVAL 60 DAY),  DATE_ADD(CURDATE(), INTERVAL 300 DAY), '置阴凉干燥处，密封保存', 'NORMAL',      NOW(), 1, 1, NOW(), 1, NOW(), 0),
(5002, 3002, 'B2025081502', DATE_SUB(CURDATE(), INTERVAL 500 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY),  '阴凉干燥，防异味',       'NEAR_EXPIRY', NOW(), 1, 1, NOW(), 1, NOW(), 0),
(5003, 3003, 'B2025030103', DATE_SUB(CURDATE(), INTERVAL 600 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY),  '常温干燥密封',           'EXPIRED',     NOW(), 1, 1, NOW(), 1, NOW(), 0),
(5004, 3006, 'B2026072006', DATE_SUB(CURDATE(), INTERVAL 30 DAY),  DATE_ADD(CURDATE(), INTERVAL 510 DAY), '密封置阴凉干燥处',       'NORMAL',      NOW(), 1, 1, NOW(), 1, NOW(), 0);
