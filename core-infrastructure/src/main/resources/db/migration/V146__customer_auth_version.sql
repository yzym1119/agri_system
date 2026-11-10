-- 主档撤销代次独立于身份行版本及普通业务version；不改客户状态、归属或钱货。
-- 已有token缺customerAuthVersion须重登，不按默认1猜补旧会话。
ALTER TABLE customer ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 1 COMMENT '客户登录撤销版本，状态或删除变化递增' AFTER status;
