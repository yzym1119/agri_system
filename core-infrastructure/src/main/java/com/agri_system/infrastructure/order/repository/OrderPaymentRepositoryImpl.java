package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.infrastructure.order.mapper.OrderPaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单支付明细仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class OrderPaymentRepositoryImpl implements OrderPaymentRepository {
    
    private final OrderPaymentMapper mapper;
    
    @Override
    public OrderPayment save(OrderPayment payment) {
        if (payment.getId() == null) {
            mapper.insert(payment);
        } else {
            mapper.update(payment);
        }
        return payment;
    }
    
    @Override
    public void saveAll(List<OrderPayment> payments) {
        for (OrderPayment payment : payments) {
            save(payment);
        }
    }
    
    @Override
    public List<OrderPayment> findByOrderId(Long orderId) {
        return mapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("order_id = ?", orderId)
                        .orderBy("created_at", true)
        );
    }
}

