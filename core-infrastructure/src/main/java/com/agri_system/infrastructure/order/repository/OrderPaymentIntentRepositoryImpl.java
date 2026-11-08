package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderPaymentIntent;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.infrastructure.order.mapper.OrderPaymentIntentMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 订单外部支付意图仓储实现
 */
@Repository
@RequiredArgsConstructor
public class OrderPaymentIntentRepositoryImpl implements OrderPaymentIntentRepository {

    private final OrderPaymentIntentMapper mapper;

    @Override
    public OrderPaymentIntent save(OrderPaymentIntent intent) {
        if (intent.getId() == null) {
            mapper.insert(intent);
        } else {
            mapper.update(intent);
        }
        return intent;
    }

    @Override
    public Optional<OrderPaymentIntent> findByIntentNo(String intentNo) {
        OrderPaymentIntent intent = mapper.selectOneByQuery(QueryWrapper.create()
                .where("intent_no = ?", intentNo));
        return Optional.ofNullable(intent);
    }

    @Override
    public Optional<OrderPaymentIntent> findByRequestKey(String requestKey) {
        OrderPaymentIntent intent = mapper.selectOneByQuery(QueryWrapper.create()
                .where("request_key = ?", requestKey));
        return Optional.ofNullable(intent);
    }

    @Override
    public int expirePendingByOrderId(Long orderId) {
        return mapper.expirePendingByOrderId(orderId);
    }

    @Override
    public int claimPendingByIntentNo(String intentNo) {
        return mapper.claimPendingByIntentNo(intentNo);
    }

    @Override
    public int expirePendingByIntentNo(String intentNo) {
        return mapper.expirePendingByIntentNo(intentNo);
    }
}
