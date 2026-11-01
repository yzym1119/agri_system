-- 既有更新时间为秒级，不能区分同秒改派及甲→乙→甲；版本不代表历史改派次数。
ALTER TABLE order_delivery ADD COLUMN assignment_version BIGINT NOT NULL DEFAULT 0 COMMENT '当前指派版本，明确改派时递增';
