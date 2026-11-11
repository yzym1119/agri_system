-- 仅处理元数据：保留搜索成功后生成未知的分阶段用量，不改原问题/摘要/收据或V150。
ALTER TABLE agronomy_case_entry
    ADD COLUMN assistant_steps TEXT NULL,
    ADD CONSTRAINT chk_assistant_steps CHECK (assistant_steps IS NULL OR (
        type='ASSISTANT_REQUEST' AND assistant_state IS NOT NULL
        AND JSON_VALID(assistant_steps)
    ));
