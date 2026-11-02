-- V10 创建的 staff_performance 自 V45 起由 staff_performance_log 取代；
-- 全仓 .java/.xml/.vue/.ts 零代码引用（已逐项核实），删除遗留空表。
-- 注意：inventory_warning、customer_follow_up、order_payment_intent 曾被误判为死表，
-- 实际均有活跃 Service/Controller/测试，不在本迁移范围内。
DROP TABLE IF EXISTS `staff_performance`;
