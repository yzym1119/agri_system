-- POS请求结果和取消屏障，不记录第二套订单金额、库存或支付数据。
CREATE TABLE pos_sale_submission (
    request_key VARCHAR(36) NOT NULL,
    store_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    request_hash CHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    order_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_key),
    KEY idx_pos_sale_submission_actor (store_id,staff_id,created_at),
    CONSTRAINT chk_pos_sale_submission_result CHECK (
      (status='PENDING' AND order_id IS NULL) OR
      (status='CANCELLED' AND order_id IS NULL) OR
      (status='COMPLETED' AND order_id IS NOT NULL AND request_hash IS NOT NULL)
    )
) ENGINE=InnoDB;
