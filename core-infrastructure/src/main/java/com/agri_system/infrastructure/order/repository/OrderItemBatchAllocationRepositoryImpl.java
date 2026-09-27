package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.infrastructure.order.mapper.OrderItemBatchAllocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单行批次归属事实仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class OrderItemBatchAllocationRepositoryImpl implements OrderItemBatchAllocationRepository {

    private final OrderItemBatchAllocationMapper mapper;

    @Override
    public OrderItemBatchAllocation save(OrderItemBatchAllocation allocation) {
        if (allocation.getId() == null) {
            mapper.insert(allocation);
        } else {
            mapper.update(allocation);
        }
        return allocation;
    }

    @Override
    public List<OrderItemBatchAllocation> findByOrderId(Long orderId) {
        return mapper.findByOrderId(orderId);
    }

    @Override
    public List<OrderItemBatchAllocation> findByRequestId(Long pickupRequestId) {
        return mapper.findByRequestId(pickupRequestId);
    }

    @Override
    public List<OrderItemBatchAllocation> findByOrderItemId(Long orderItemId) {
        return mapper.findByOrderItemId(orderItemId);
    }
}
