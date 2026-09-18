-- =====================================================================
-- V10: 阶段5 预警演示种子（相对部署日，扫描即可见临期/过期场景）
-- SLA 场景依赖在途检验任务，由冒烟/演示链路动态构造，不做静态播种。
-- 幂等：固定主键 + NOT EXISTS，可重复执行不报错。
-- =====================================================================

-- 1) 临期证照：杭州芳蕾花茶（1003），到期日=当前日+20 天（阈值 60 天内，警告级）
INSERT INTO qc_supplier_license
    (id, supplier_id, license_type, cert_no, valid_from, valid_to, status, tenant_id, created_at, updated_at, deleted)
SELECT 9101, 1003, '食品经营许可证', 'DEMO-LIC-NEAR-2026',
       DATE_SUB(CURDATE(), INTERVAL 345 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY),
       1, 1, NOW(), NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM qc_supplier_license WHERE id = 9101);

-- 2) 近效期批次：SKU 3001，效期=当前日+15 天
INSERT INTO qc_batch
    (id, sku_id, batch_no, production_date, expiry_date, storage_condition, status,
     last_check_at, tenant_id, created_at, updated_at, deleted)
SELECT 9201, 3001, 'DEMO-NEAR',
       DATE_SUB(CURDATE(), INTERVAL 350 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY),
       '阴凉干燥处', 'NEAR_EXPIRY', NOW(), 1, NOW(), NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM qc_batch WHERE id = 9201);

-- 3) 已过期批次：SKU 3004，效期=当前日-10 天
INSERT INTO qc_batch
    (id, sku_id, batch_no, production_date, expiry_date, storage_condition, status,
     last_check_at, tenant_id, created_at, updated_at, deleted)
SELECT 9202, 3004, 'DEMO-EXPIRED',
       DATE_SUB(CURDATE(), INTERVAL 380 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY),
       '阴凉干燥处', 'EXPIRED', NOW(), 1, NOW(), NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM qc_batch WHERE id = 9202);
