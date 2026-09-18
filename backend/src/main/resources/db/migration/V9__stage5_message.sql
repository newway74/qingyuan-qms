-- =====================================================================
-- V9: 阶段5 站内消息中心
-- 待办 qc_todo 已在 V1 建表（含 ALERT 类型），本脚本仅新增消息表与权限。
-- =====================================================================

CREATE TABLE qc_message (
    id                      BIGINT       NOT NULL,
    receiver_id             BIGINT       NULL COMMENT '为空=角色消息（该角色所有人可见）',
    role_code               VARCHAR(64)  NULL,
    msg_type                VARCHAR(30)  NOT NULL COMMENT 'TODO待办通知/ALERT预警/SYSTEM系统',
    title                   VARCHAR(200) NOT NULL,
    content                 VARCHAR(500) NULL,
    biz_type                VARCHAR(50)  NULL,
    biz_id                  BIGINT       NULL,
    is_read                 TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读1已读',
    read_at                 DATETIME     NULL,
    tenant_id               BIGINT       NOT NULL DEFAULT 1,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_msg_receiver (receiver_id, is_read, created_at),
    KEY idx_msg_role (role_code, is_read),
    KEY idx_msg_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- 消息中心权限（挂工作台组 1xx）
INSERT INTO sys_permission (id, parent_id, perm_code, name, type, path, icon, sort, tenant_id) VALUES
(102, 101, 'message:list', '站内消息查看', 'BUTTON', NULL, NULL, 1, 1),
(103, 101, 'message:read', '消息标记已读', 'BUTTON', NULL, NULL, 2, 1);

-- ADMIN 通配 * 已覆盖；四业务角色授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.id
FROM sys_permission p
JOIN (
    SELECT 2 AS role_id, 'message:list' AS perm_code
    UNION ALL SELECT 2, 'message:read'
    UNION ALL SELECT 3, 'message:list'
    UNION ALL SELECT 3, 'message:read'
    UNION ALL SELECT 4, 'message:list'
    UNION ALL SELECT 4, 'message:read'
    UNION ALL SELECT 5, 'message:list'
    UNION ALL SELECT 5, 'message:read'
) r ON r.perm_code = p.perm_code;
