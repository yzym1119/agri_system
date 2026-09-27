package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderPickupRequest;

import java.util.List;
import java.util.Optional;

/** 库房取货请求仓储。 */
public interface OrderPickupRequestRepository {

    OrderPickupRequest save(OrderPickupRequest request);

    Optional<OrderPickupRequest> findById(Long id);

    Optional<OrderPickupRequest> findByIdForUpdate(Long id);

    Optional<OrderPickupRequest> findPendingByOrderItemId(Long orderItemId);

    /**
     * 按门店 + 状态查询取货请求（按 id 升序）
     */
    List<OrderPickupRequest> findByStoreIdAndStatus(Long storeId, String status);
}
