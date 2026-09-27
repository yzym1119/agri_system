package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderItemPriceAudit;
import com.agri_system.domain.order.repository.OrderItemPriceAuditRepository;
import com.agri_system.infrastructure.order.mapper.OrderItemPriceAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单行价格审计事实仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class OrderItemPriceAuditRepositoryImpl implements OrderItemPriceAuditRepository {

    private final OrderItemPriceAuditMapper mapper;

    @Override
    public OrderItemPriceAudit save(OrderItemPriceAudit audit) {
        if (audit.getId() == null) {
            mapper.insert(audit);
        } else {
            mapper.update(audit);
        }
        return audit;
    }

    @Override
    public List<OrderItemPriceAudit> findByOrderItemId(Long orderItemId) {
        return mapper.findByOrderItemId(orderItemId);
    }
}
