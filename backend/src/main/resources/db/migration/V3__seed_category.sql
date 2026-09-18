-- =====================================================================
-- V3: 品类种子（四大品类）。阶段1作为样例CRUD的可见数据。
-- =====================================================================
INSERT INTO qc_category (id, parent_id, code, name, sort, status, tenant_id, created_at, updated_at) VALUES
(100, 0, 'TCM',           '中药饮片',   10, 1, 1, NOW(), NOW()),
(200, 0, 'NOURISHING',    '滋补养生食材', 20, 1, 1, NOW(), NOW()),
(300, 0, 'HERBAL_TEA',    '花茶',       30, 1, 1, NOW(), NOW()),
(400, 0, 'HEALTH_FOOD',   '健康食品',   40, 1, 1, NOW(), NOW()),
-- 二级品类示例
(201, 200, 'DRIED_FRUIT', '果干类',     1, 1, 1, NOW(), NOW()),
(202, 200, 'CHENPI',      '陈皮类',     2, 1, 1, NOW(), NOW()),
(301, 300, 'ROSE_TEA',    '玫瑰花茶',   1, 1, 1, NOW(), NOW()),
(302, 300, 'CHRYSANTHEMUM_TEA', '菊花茶', 2, 1, 1, NOW(), NOW());
