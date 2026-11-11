-- 用户结束等待与外部执行/费用事实独立，晚结果不能覆盖原结束屏障。
-- 不改原请求/收据，不回填，不释放已派出的最大预占。
ALTER TABLE agronomy_case_entry
    ADD COLUMN assistant_closed_at DATETIME NULL,
    ADD CONSTRAINT chk_assistant_customer_closure CHECK (assistant_closed_at IS NULL OR (
        type='ASSISTANT_REQUEST' AND actor_type='CUSTOMER'
        AND assistant_state IS NOT NULL AND assistant_state IN ('RUNNING','UNKNOWN','COMPLETED')
        AND assistant_claim_key IS NOT NULL
    ));
