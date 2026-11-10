-- 内部门店启用不是公开授权；默认不公开。公开版本独立于普通资料，防止旧切换循环覆盖。
ALTER TABLE store ADD COLUMN client_visible TINYINT NOT NULL DEFAULT 0 COMMENT '客户公开资料：0未公开1公开';
ALTER TABLE store ADD COLUMN client_publication_version BIGINT NOT NULL DEFAULT 1 COMMENT '公开设置版本，只由独立入口递增';

-- 不自动授角色。实际角色须同时具备store:view与store:publish，非管理员仅本店。
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT COALESCE((SELECT id FROM sys_menu WHERE permission='store:list' AND type=1 ORDER BY id LIMIT 1),0),
       '公开门店资料','store:publish',2,NULL,NULL,50,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='store:publish');
