-- =====================================================================
-- V16：商品全流程品控台账 - 品控工作台（统计主页）菜单权限
-- 1. 新增工作台菜单权限（台账组首页），授予全部业务角色（只读统计）；
-- 2. 三类报表导出（商品台账/全流程记录/资料缺口）统一复用既有 ledger:goods:export（已授予质量主管角色 5）。
-- 说明：仅新增 sys_permission / sys_role_permission 行，不改动任何历史对象。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, name, type, path, icon, sort, tenant_id) VALUES
(1223, 0, 'ledger:dashboard:view', '品控工作台', 'MENU', '/ledger/dashboard', 'DashboardOutlined', 10, 1);

-- ADMIN 通配 * 已覆盖；业务角色（2 抽样收样员 / 3 检验员 / 4 复核判定人 / 5 质量主管）均可查看台账工作台
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM sys_permission p
JOIN (
    SELECT 2 AS role_id
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
) r ON p.perm_code = 'ledger:dashboard:view';
