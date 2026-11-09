-- 员工本人本店订单只读；不扩大为整店订单或任何写权限，不自动授予角色。
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT COALESCE((SELECT id FROM sys_menu WHERE permission='order' AND type=1 ORDER BY id LIMIT 1),0),
       '本人订单查询','order:own:query',2,NULL,NULL,36,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='order:own:query');
