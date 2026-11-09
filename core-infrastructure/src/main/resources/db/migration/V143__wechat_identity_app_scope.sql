-- 旧记录AppID未知，保留空命名空间；禁止根据当前配置猜测归属。
-- 单条ALTER保留原身份/客户/状态/凭据，不修改钱包、订单或角色。
ALTER TABLE customer_identity
    ADD COLUMN app_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '身份AppID；空串仅为未知历史' AFTER identity_type,
    MODIFY COLUMN identity_value VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '大小写敏感身份值',
    DROP INDEX uk_identity_type_value,
    ADD UNIQUE KEY uniq_customer_identity_scope (identity_type,app_id,identity_value);
