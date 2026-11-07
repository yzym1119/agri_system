package com.agri_system.domain.customer.repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 专用金发放持久请求仓储（幂等协调）。
 *
 * <p>与 POS 收银请求（pos_sale_submission）同一模式：请求键为主键，
 * reserve 以「插入即占位」实现去重；COMPLETED 后重放返回已记录结果。</p>
 */
public interface SpecialFundGrantRequestRepository {

    /** 占位插入；request_key 已存在时不覆盖任何字段。 */
    void reserve(String requestKey, Long customerId, BigDecimal amount);

    /**
     * 按请求键查询；lock=true 时加 FOR UPDATE 行锁（需在事务内调用）。
     */
    Optional<GrantRequest> find(String requestKey, boolean lock);

    /** 将 PENDING 置为 COMPLETED 并记录发放后余额；非 PENDING 状态更新失败抛出。 */
    void complete(String requestKey, BigDecimal balanceAfter);

    /** 专用金发放请求快照。 */
    record GrantRequest(String requestKey, Long customerId, BigDecimal amount,
                        BigDecimal balanceAfter, String status) {
        public boolean completed() {
            return "COMPLETED".equals(status);
        }
    }
}
