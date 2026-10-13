package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderItemBatchAllocation;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.infrastructure.order.mapper.OrderItemBatchAllocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public List<OrderItemBatchAllocation> findByRequestIds(java.util.Collection<Long> pickupRequestIds) {
        if (pickupRequestIds == null || pickupRequestIds.isEmpty()) return List.of();
        return mapper.selectListByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .where("pickup_request_id IN (" + pickupRequestIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(",")) + ")", pickupRequestIds.toArray())
                .orderBy("id", true));
    }

    @Override
    public List<OrderItemBatchAllocation> findByOrderItemId(Long orderItemId) {
        return mapper.findByOrderItemId(orderItemId);
    }

    @Override
    public List<WindowMargin> sumWindowMarginByProduct(int days, Long storeId) {
        List<Map<String, Object>> rows = mapper.sumWindowMarginByProduct(days, storeId);
        List<WindowMargin> margins = new ArrayList<>();
        if (rows == null) {
            return margins;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long productId = toLong(pick(row, "productId", "product_id"));
            if (productId == null) {
                continue;
            }
            margins.add(new WindowMargin(productId,
                    toBigDecimal(pick(row, "revenue")),
                    toBigDecimal(pick(row, "totalCost", "total_cost")),
                    toInteger(pick(row, "orderCount", "order_count"))));
        }
        return margins;
    }

    private static Object pick(Map<String, Object> row, String... candidates) {
        for (String candidate : candidates) {
            if (row.containsKey(candidate)) {
                return row.get(candidate);
            }
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(candidate)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        try { return new BigDecimal(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return null; }
    }
}
