package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单明细仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {
    
    private final OrderItemMapper mapper;
    
    @Override
    public OrderItem save(OrderItem item) {
        if (item.getId() == null) {
            mapper.insert(item);
        } else {
            mapper.update(item);
        }
        return item;
    }
    
    @Override
    public void saveAll(List<OrderItem> items) {
        for (OrderItem item : items) {
            save(item);
        }
    }
    
    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return mapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("order_id = ?", orderId)
                        .orderBy("sort_order", true)
        );
    }
    
    @Override
    public OrderItem findById(Long id) {
        return mapper.selectOneById(id);
    }
}

