package com.agri_system.domain.marketing.points.repository;

import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;

import java.util.List;
import java.util.Optional;

public interface PointsExchangeOrderRepository {

    PointsExchangeOrder save(PointsExchangeOrder order);

    Optional<PointsExchangeOrder> findById(Long id);

    Optional<PointsExchangeOrder> findByIdWithLock(Long id);

    Optional<PointsExchangeOrder> findByPickupCode(String pickupCode);

    Optional<PointsExchangeOrder> findByPickupCodeForUpdate(String pickupCode);

    List<PointsExchangeOrder> findByCustomer(Long customerId, int offset, int limit);

    long countByCustomer(Long customerId);

    List<PointsExchangeOrder> query(Long customerId, Integer status, int offset, int limit);

    long count(Long customerId, Integer status);

    long countByGoodsId(Long goodsId);

    long countActiveByCustomerId(Long customerId);
}
