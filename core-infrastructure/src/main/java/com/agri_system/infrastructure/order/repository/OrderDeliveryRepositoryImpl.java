package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.infrastructure.order.mapper.OrderDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 订单配送仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class OrderDeliveryRepositoryImpl implements OrderDeliveryRepository {
    
    private final OrderDeliveryMapper mapper;
    
    @Override
    public OrderDelivery save(OrderDelivery delivery) {
        if (delivery.getId() == null) {
            mapper.insert(delivery);
        } else {
            mapper.update(delivery);
        }
        return delivery;
    }
    
    @Override
    public Optional<OrderDelivery> findByOrderId(Long orderId) {
        OrderDelivery delivery = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("order_id = ?", orderId)
        );
        return Optional.ofNullable(delivery);
    }
    
    @Override
    public Optional<OrderDelivery> findByTrackingNo(String trackingNo) {
        OrderDelivery delivery = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("tracking_no = ?", trackingNo)
        );
        return Optional.ofNullable(delivery);
    }
}

