-- ============================================
-- V53: 添加分类编码序号
-- ============================================

-- 初始化分类编码序号（从0开始）
INSERT INTO `sys_code_sequence` (`code_type`, `last_code`, `sequence`) VALUES
('CATEGORY', 'CAT000000', 0)
ON DUPLICATE KEY UPDATE code_type = code_type;

