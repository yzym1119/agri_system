package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderPickupRequest;
import com.agri_system.domain.order.repository.OrderPickupRequestRepository;
import com.agri_system.infrastructure.order.mapper.OrderPickupRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 库房取货请求仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class OrderPickupRequestRepositoryImpl implements OrderPickupRequestRepository {

    private final OrderPickupRequestMapper mapper;

    @Override
    public OrderPickupRequest save(OrderPickupRequest request) {
        if (request.getId() == null) {
            mapper.insert(request);
        } else {
            mapper.update(request);
        }
        return request;
    }

    @Override
    public Optional<OrderPickupRequest> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }
}
