-- 专用金发放持久请求幂等（复用 V133 pos_sale_submission 的请求身份模式）：
-- 同一 request_key 的重复发放请求只允许落一次余额，重放返回已记录的结果。
-- 本表只保存请求身份与结果快照，不记录第二套余额或台账数据。
CREATE TABLE special_fund_grant_request (
    request_key VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    balance_after DECIMAL(18,2) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_key),
    KEY idx_special_fund_grant_customer (customer_id, created_at),
    CONSTRAINT chk_special_fund_grant_result CHECK (
      (status='PENDING' AND balance_after IS NULL) OR
      (status='COMPLETED' AND balance_after IS NOT NULL)
    )
) ENGINE=InnoDB;
