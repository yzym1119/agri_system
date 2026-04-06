package com.agri_system.infrastructure.marketing.points.repository;

import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import com.agri_system.infrastructure.marketing.points.mapper.PointsExchangeOrderMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PointsExchangeOrderRepositoryImpl implements PointsExchangeOrderRepository {

    private final PointsExchangeOrderMapper mapper;

    @Override
    public PointsExchangeOrder save(PointsExchangeOrder order) {
        if (order.getId() == null) {
            mapper.insert(order);
        } else {
            mapper.update(order);
        }
        return order;
    }

    @Override
    public Optional<PointsExchangeOrder> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public Optional<PointsExchangeOrder> findByIdWithLock(Long id) {
        return Optional.ofNullable(mapper.selectByIdWithLock(id));
    }

    @Override
    public Optional<PointsExchangeOrder> findByPickupCode(String pickupCode) {
        return Optional.ofNullable(mapper.selectByPickupCode(pickupCode));
    }

    @Override
    public Optional<PointsExchangeOrder> findByPickupCodeForUpdate(String pickupCode) {
        return Optional.ofNullable(mapper.selectByPickupCodeForUpdate(pickupCode));
    }

    @Override
    public List<PointsExchangeOrder> findByCustomer(Long customerId, int offset, int limit) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .orderBy("created_at DESC")
                .limit(offset, limit);
        return mapper.selectListByQuery(query);
    }

    @Override
    public long countByCustomer(Long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId);
        return mapper.selectCountByQuery(query);
    }

    @Override
    public List<PointsExchangeOrder> query(Long customerId, Integer status, int offset, int limit) {
        QueryWrapper query = QueryWrapper.create();
        boolean hasCondition = false;
        if (customerId != null) {
            query.where("customer_id = ?", customerId);
            hasCondition = true;
        }
        if (status != null) {
            if (hasCondition) {
                query.and("status = ?", status);
            } else {
                query.where("status = ?", status);
                hasCondition = true;
            }
        }
        query.orderBy("created_at DESC")
                .limit(offset, limit);
        return mapper.selectListByQuery(query);
    }

    @Override
    public long count(Long customerId, Integer status) {
        QueryWrapper query = QueryWrapper.create();
        boolean hasCondition = false;
        if (customerId != null) {
            query.where("customer_id = ?", customerId);
            hasCondition = true;
        }
        if (status != null) {
            if (hasCondition) {
                query.and("status = ?", status);
            } else {
                query.where("status = ?", status);
            }
        }
        return mapper.selectCountByQuery(query);
    }
}

