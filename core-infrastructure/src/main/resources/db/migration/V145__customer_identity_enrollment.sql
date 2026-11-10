-- 未建客户的待核验申请不能写入customer_identity；通用操作日志无防重/待办/固定结果约束。
CREATE TABLE customer_identity_enrollment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_subject CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'AppID/OpenID当前申请锚；拒绝释放，原结果不覆盖',
    app_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    open_id VARCHAR(200) COLLATE utf8mb4_bin NOT NULL,
    phone VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    verified_at DATETIME NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    review_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    customer_id BIGINT NULL,
    identity_id BIGINT NULL,
    identity_auth_version BIGINT NULL,
    decision_request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    decision_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reviewed_by BIGINT NULL,
    verification_method VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    decision_reason VARCHAR(500) NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_identity_enrollment_request(request_key),
    UNIQUE KEY uniq_identity_enrollment_active(active_subject),
    UNIQUE KEY uniq_identity_enrollment_decision(decision_request_key),
    KEY idx_identity_enrollment_queue(status,id),
    KEY idx_identity_enrollment_subject(app_id,open_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信初次开户及关联核验申请';

-- 只落真实权限项，由管理员明确授权；不自动授任何员工角色。
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT COALESCE((SELECT parent_id FROM sys_menu WHERE permission='customer:query' ORDER BY id LIMIT 1),(SELECT id FROM sys_menu WHERE permission='customer' AND type=1 ORDER BY id LIMIT 1),0),
       '微信关联核验','customer:identity:query',1,'/marketing/customer/identity-enrollments','Connection',37,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='customer:identity:query');
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT COALESCE((SELECT id FROM sys_menu WHERE permission='customer:identity:query' ORDER BY id LIMIT 1),0),
       '客户微信初次关联核验','customer:identity:bind',2,NULL,NULL,38,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='customer:identity:bind');
