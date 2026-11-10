-- C2第一批：原客户/门店共用问题事实，不建另一套客户、订单或AI会话账。
CREATE TABLE agronomy_case (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question VARCHAR(2000) NOT NULL,
    region VARCHAR(100) NOT NULL DEFAULT '',
    crop VARCHAR(50) NOT NULL DEFAULT '',
    growth_stage VARCHAR(100) NOT NULL DEFAULT '',
    status VARCHAR(24) NOT NULL DEFAULT 'COLLECTING',
    version BIGINT NOT NULL DEFAULT 1,
    facts_version BIGINT NOT NULL DEFAULT 1,
    assigned_staff_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uniq_case_creation (customer_id,request_key),
    KEY idx_case_customer_page (customer_id,id),
    KEY idx_case_store_page (store_id,id),
    KEY idx_case_store_status (store_id,status,id),
    KEY idx_case_assigned_page (assigned_staff_id,store_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 追加记录不可覆盖；每个真实操作者原请求同时保存不可变收据，不额外建请求表。
CREATE TABLE agronomy_case_entry (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(24) NOT NULL,
    body TEXT NOT NULL,
    source_text TEXT NOT NULL,
    region VARCHAR(100) NOT NULL DEFAULT '',
    crop VARCHAR(50) NOT NULL DEFAULT '',
    growth_stage VARCHAR(100) NOT NULL DEFAULT '',
    assigned_staff_id BIGINT NULL,
    expected_version BIGINT NOT NULL,
    result_version BIGINT NOT NULL,
    result_facts_version BIGINT NOT NULL,
    result_status VARCHAR(24) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uniq_case_actor_request (actor_type,actor_id,request_key),
    KEY idx_case_entry_page (case_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 独立真实菜单：只登记权限，不自动授给角色。普通query仅本人已分配，manage才可读本店全部。
INSERT INTO sys_menu(parent_id,name,permission,type,path,icon,sort_order,visible)
SELECT 0,'农技问题','agronomy:case:query',1,'/agronomy/cases','ChatDotRound',66,1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE permission='agronomy:case:query');
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,visible)
SELECT id,'查看本店队列与分配','agronomy:case:manage',2,1,1 FROM sys_menu
WHERE permission='agronomy:case:query' AND NOT EXISTS(SELECT 1 FROM (SELECT permission FROM sys_menu) x WHERE x.permission='agronomy:case:manage');
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,visible)
SELECT id,'人工农技回复','agronomy:case:review',2,2,1 FROM sys_menu
WHERE permission='agronomy:case:query' AND NOT EXISTS(SELECT 1 FROM (SELECT permission FROM sys_menu) x WHERE x.permission='agronomy:case:review');
