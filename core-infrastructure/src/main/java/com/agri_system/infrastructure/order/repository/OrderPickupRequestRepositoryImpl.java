package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderPickupRequest;
import com.agri_system.domain.order.repository.OrderPickupRequestRepository;
import com.agri_system.infrastructure.order.mapper.OrderPickupRequestMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Override
    public Optional<OrderPickupRequest> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    @Override
    public Optional<OrderPickupRequest> findPendingByOrderItemId(Long orderItemId) {
        return Optional.ofNullable(mapper.findPendingByOrderItemId(orderItemId));
    }

    @Override
    public List<OrderPickupRequest> findByStoreIdAndStatus(Long storeId, String status) {
        QueryWrapper query = QueryWrapper.create().where("store_id = ?", storeId)
                .and("status = ?", status).orderBy("id ASC");
        return mapper.selectListByQuery(query);
    }
}
