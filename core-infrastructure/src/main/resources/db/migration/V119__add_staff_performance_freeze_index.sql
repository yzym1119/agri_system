-- staff_performance_log 可发放提成查询索引优化
-- 结算 sumPayableByFreezeMonth(staff_id + status=2 + freeze_until BETWEEN)
-- 与工资单明细 findPayableByStaffAndFreezeMonth(staff_id + status=2 + freeze_until 排序)
-- 均按员工维度过滤;而 V45 仅单列 idx_staff、V118 仅全局 idx_freeze(status,freeze_until),
-- 无法一次命中 staff_id+status+freeze_until,需回表过滤。补复合索引消除回表。
-- 全局类查询(unfreezeDue 日解冻、findStaffIdsWithPayableInMonth)不带 staff_id,仍走 idx_freeze,故保留不删。
ALTER TABLE staff_performance_log
  ADD INDEX idx_staff_status_freeze (staff_id, status, freeze_until);
