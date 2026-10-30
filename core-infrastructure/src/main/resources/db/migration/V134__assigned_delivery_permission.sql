-- V134: 独立授予本人配送查询/签收，不能通过此权限取得整店发货或订单查询。
-- 仅增加既有权限表的一项，不自动为任何现有员工/角色扩大权限。
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT COALESCE((SELECT id FROM sys_menu WHERE permission='order' AND type=1 ORDER BY id LIMIT 1),0),
       '本人配送','order:delivery:assigned',2,NULL,NULL,35,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='order:delivery:assigned');
