-- 提成冻结生命周期:status(1-冻结 2-已解冻) + freeze_until(解冻时点)
ALTER TABLE staff_performance_log
  ADD COLUMN status TINYINT NOT NULL DEFAULT 2
      COMMENT '1-冻结 2-已解冻' AFTER need_review,
  ADD COLUMN freeze_until DATETIME NULL
      COMMENT '解冻时点:冻结类=created_at+冻结期,非冻结类=created_at';

-- 存量行:status 默认 2(已解冻);freeze_until 回填 created_at,保持原"按发生月计入"行为
UPDATE staff_performance_log SET freeze_until = created_at WHERE freeze_until IS NULL;

-- 日解冻任务按 (status, freeze_until) 扫描
ALTER TABLE staff_performance_log ADD INDEX idx_freeze (status, freeze_until);
