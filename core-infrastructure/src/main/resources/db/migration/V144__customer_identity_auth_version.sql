-- 身份授权版本只记录撤销代次；旧身份保持归属、启用状态及AppID不变。
-- 旧会话缺版本必须重新登录，禁止以本字段初始值猜补旧token。
ALTER TABLE customer_identity
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 1 COMMENT '身份授权版本，关联或状态变化递增' AFTER status;
