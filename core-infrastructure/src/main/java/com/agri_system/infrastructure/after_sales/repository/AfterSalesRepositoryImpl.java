package com.agri_system.infrastructure.after_sales.repository;

import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.mybatisflex.core.paginate.Page;
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

    @Override
    public Page<AfterSales> findPage(Integer pageNum,
                                     Integer pageSize,
                                     String keyword,
                                     Long storeId,
                                     Integer type,
                                     Integer status,
                                     String startTime,
                                     String endTime) {
        QueryWrapper query = QueryWrapper.create()
                .from(AfterSales.class).as("a")
                .leftJoin("order").as("o").on("a.order_id = o.id")
                .leftJoin("customer").as("c").on("a.customer_id = c.id");

        if (keyword != null && !keyword.trim().isEmpty()) {
            String likeKeyword = "%" + keyword.trim() + "%";
            query.and("(a.after_sales_no LIKE ? OR o.order_no LIKE ? OR c.name LIKE ? OR c.phone LIKE ?)",
                    likeKeyword, likeKeyword, likeKeyword, likeKeyword);
        }

        if (storeId != null) {
            query.and("a.store_id = ?", storeId);
        }
        if (type != null) {
            query.and("a.type = ?", type);
        }
        if (status != null) {
            query.and("a.status = ?", status);
        }
        if (startTime != null && !startTime.trim().isEmpty()) {
            query.and("a.created_at >= ?", java.time.LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            query.and("a.created_at <= ?", java.time.LocalDateTime.parse(endTime));
        }

        query.orderBy("a.created_at DESC");
        return mapper.paginate(new Page<>(pageNum, pageSize), query);
    }

    @Override
    public long countActiveByCustomerId(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .and("status IN (10, 40)");
        return mapper.selectCountByQuery(query);
    }
}






