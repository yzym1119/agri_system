-- C1-B唯一内容来源；草稿/审核/发布均引用同一不可覆盖正文版本。不预填任何农技知识。
CREATE TABLE agronomy_knowledge_revision (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    logical_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision INT NOT NULL,
    store_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL DEFAULT '',
    crop VARCHAR(50) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    region VARCHAR(100) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    growth_stage VARCHAR(100) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    applies_to TEXT NOT NULL,
    exclusions TEXT NOT NULL,
    body MEDIUMTEXT NOT NULL,
    source_text TEXT NOT NULL,
    audience VARCHAR(16) NOT NULL,
    risk_level VARCHAR(24) NOT NULL,
    effective_from DATE NULL,
    effective_until DATE NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    published_at DATETIME NULL,
    withdrawn_at DATETIME NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uniq_knowledge_revision (logical_key,revision),
    UNIQUE KEY uniq_knowledge_submission (created_by,request_key),
    KEY idx_knowledge_store_page (store_id,id),
    KEY idx_knowledge_public_scope (state,audience,risk_level,region,crop,growth_stage,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 单独真实菜单与操作项；不自动授角色，不以客户端入口代替后台权限。
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT 0,'农技知识','agronomy:knowledge:query',1,'/agronomy/knowledge','Reading',65,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='agronomy:knowledge:query');
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,visible)
SELECT id,'保存知识草稿','agronomy:knowledge:write',2,1,1 FROM sys_menu
WHERE permission='agronomy:knowledge:query' AND NOT EXISTS(SELECT 1 FROM (SELECT permission FROM sys_menu) x WHERE x.permission='agronomy:knowledge:write');
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,visible)
SELECT id,'审核知识','agronomy:knowledge:review',2,2,1 FROM sys_menu
WHERE permission='agronomy:knowledge:query' AND NOT EXISTS(SELECT 1 FROM (SELECT permission FROM sys_menu) x WHERE x.permission='agronomy:knowledge:review');
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,visible)
SELECT id,'发布或撤回知识','agronomy:knowledge:publish',2,3,1 FROM sys_menu
WHERE permission='agronomy:knowledge:query' AND NOT EXISTS(SELECT 1 FROM (SELECT permission FROM sys_menu) x WHERE x.permission='agronomy:knowledge:publish');
