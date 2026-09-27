package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderPickupRequest;

import java.util.Optional;

/** 库房取货请求仓储。 */
public interface OrderPickupRequestRepository {

    OrderPickupRequest save(OrderPickupRequest request);

    Optional<OrderPickupRequest> findById(Long id);

    Optional<OrderPickupRequest> findByIdForUpdate(Long id);

    Optional<OrderPickupRequest> findPendingByOrderItemId(Long orderItemId);
}
