-- =====================================================================
-- V8: 阶段4 权限补充——批次效期台账菜单与按钮
-- 不合格处置(defect:*)/供应商整改(rect:*)/发起复检(inspection:task:recheck)
-- 已在 V2 播种，无需重复。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, name, type, path, icon, sort, tenant_id) VALUES
(421, 0,   'batch:list',       '批次效期',   'MENU', '/batches', 'DatabaseOutlined', 42, 1),
(422, 421, 'batch:view',       '批次详情',   'BUTTON', NULL, NULL, 1, 1),
(423, 421, 'batch:recompute',  '效期重算',   'BUTTON', NULL, NULL, 2, 1);

-- ADMIN 通配 * 已覆盖；业务角色显式授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM sys_permission p
JOIN (
    SELECT 2 AS role_id, 'batch:list'  AS perm_code
    UNION ALL SELECT 2, 'batch:view'
    UNION ALL SELECT 3, 'batch:list'
    UNION ALL SELECT 3, 'batch:view'
    UNION ALL SELECT 4, 'batch:list'
    UNION ALL SELECT 4, 'batch:view'
    UNION ALL SELECT 5, 'batch:list'
    UNION ALL SELECT 5, 'batch:view'
    UNION ALL SELECT 5, 'batch:recompute'
) r ON r.perm_code = p.perm_code;
