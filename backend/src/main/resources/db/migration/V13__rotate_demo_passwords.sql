-- =============================================================
-- V13: 轮换全部内置演示账号的初始密码（开源安全加固）
-- -------------------------------------------------------------
-- 背景：V2 种子账号使用弱密码（admin/admin123、其余 Qms@12345），
--       代码仓库开源后，若有人直接把系统暴露到内网/公网而忘记改密，
--       会被按公开密码轻易登录。现将全部 5 个演示账号统一轮换为
--       强度更高的演示密码 Qms@Demo2026。
-- 约束：只做 UPDATE，不改动 V1~V12 历史迁移；已部署环境升级时
--       仅在用户仍使用种子哈希的情况下更新，改过密码的账号不受影响
--       （以 V2 种子哈希为幂等守卫，避免覆盖用户自己的密码）。
-- 提醒：演示账号仅供本地/演示环境使用，正式上线请在"个人中心"
--       修改密码，或直接停用/删除非必需的演示账号。
-- =============================================================

-- 1) admin：V2 种子哈希对应旧密码 admin123
UPDATE sys_user
SET password_hash = '$2a$10$z7VeIhVaDtocaCdIVuKaGOftgS1ahg5.4IR/fKf.nPC0Kb9q/TnGW'
WHERE username = 'admin'
  AND password_hash = '$2a$10$Fg2LQgdEKvHfbR/a5vyav.cAGzFqTdqEDgPUp5PeTFr.0oF.vIgiK';

-- 2) sampler/inspector/reviewer/qamanager：V2 种子哈希对应旧密码 Qms@12345
UPDATE sys_user
SET password_hash = '$2a$10$z7VeIhVaDtocaCdIVuKaGOftgS1ahg5.4IR/fKf.nPC0Kb9q/TnGW'
WHERE username IN ('sampler', 'inspector', 'reviewer', 'qamanager')
  AND password_hash = '$2a$10$tgmoQ.yesWd3uZlCZlukhOwASBexWqYUnU0wQtYgaxmkYqPWRrQ.S';
