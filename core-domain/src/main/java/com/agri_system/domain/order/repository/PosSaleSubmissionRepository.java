package com.agri_system.domain.order.repository;

import java.util.Optional;

/**
 * POS原请求身份与持久结果事实；同一请求行锁先于订单钱货操作。
 * 实现必须参与调用方同一事务，不能以缓存结果代替持久事实或另开写事务。
 */
public interface PosSaleSubmissionRepository {
    record Submission(String requestKey, Long storeId, Long staffId, String requestHash, String status, Long orderId) {}

    /** 争用唯一请求身份；已存在时保留原状态和归属，不覆盖。 */
    void reserve(String key, Long store, Long staff);

    /** lock为true时锁定原请求事实；UNKNOWN读取不代表请求未成交。 */
    Optional<Submission> find(String key, boolean lock);

    /** 原PENDING请求与订单钱货一起提交完成结果。 */
    void complete(String key, String hash, Long order);

    /** 原PENDING请求持久取消屏障，阻止迟到请求成交。 */
    void cancel(String key);
}
