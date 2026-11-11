-- 不新增会话表。仅扩原追加记录的处理元数据，旧原文/请求摘要/版本收据不改不回填。
ALTER TABLE agronomy_case_entry
    ADD COLUMN assistant_state VARCHAR(16) NULL,
    ADD COLUMN assistant_snapshot TEXT NULL,
    ADD COLUMN assistant_outcome TEXT NULL,
    ADD COLUMN assistant_budget_day DATE NULL,
    ADD COLUMN assistant_model_reserved_microusd BIGINT NULL,
    ADD COLUMN assistant_search_reserved_microusd BIGINT NULL,
    ADD COLUMN assistant_claim_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN assistant_updated_at DATETIME NULL,
    ADD KEY idx_assistant_day_case (assistant_budget_day,case_id),
    ADD CONSTRAINT chk_assistant_metadata CHECK (assistant_state IS NULL OR (
        type='ASSISTANT_REQUEST' AND actor_type='CUSTOMER'
        AND assistant_state IN ('QUEUED','RUNNING','COMPLETED','FAILED','UNKNOWN')
        AND assistant_snapshot IS NOT NULL AND assistant_budget_day IS NOT NULL
        AND assistant_model_reserved_microusd IS NOT NULL AND assistant_model_reserved_microusd>=0
        AND assistant_search_reserved_microusd IS NOT NULL AND assistant_search_reserved_microusd>=0
        AND assistant_updated_at IS NOT NULL
        AND (assistant_state NOT IN ('RUNNING','UNKNOWN') OR assistant_claim_key IS NOT NULL)
    ));
