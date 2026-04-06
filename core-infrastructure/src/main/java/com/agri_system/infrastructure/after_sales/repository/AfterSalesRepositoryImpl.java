package com.agri_system.infrastructure.after_sales.repository;

import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 售后仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AfterSalesRepositoryImpl implements AfterSalesRepository {

    private final AfterSalesMapper mapper;

    @Override
    public AfterSales save(AfterSales afterSales) {
        if (afterSales.getId() == null) {
            mapper.insert(afterSales);
        } else {
            mapper.update(afterSales);
        }
        return afterSales;
    }

    @Override
    public Optional<AfterSales> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public Optional<AfterSales> findProcessingByOrderId(Long orderId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where("order_id = ?", orderId)
                .and("status IN (10, 40)")
                .orderBy("id DESC")
                .limit(1);
        return Optional.ofNullable(mapper.selectOneByQuery(wrapper));
    }

    @Override
    public List<AfterSales> findByOrderId(Long orderId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where("order_id = ?", orderId)
                .orderBy("id DESC");
        return mapper.selectListByQuery(wrapper);
    }
}








