package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 订单明细仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {
    
    private final OrderItemMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    
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
    public java.util.Set<Long> findOrderIdsWithReturnableItems(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return java.util.Set.of();
        String placeholders=String.join(",",java.util.Collections.nCopies(orderIds.size(),"?"));
        // 与详情一致：待处理/待退款/已完成售后占用数量，拒绝的不占用；不按金额或三行概览猜物料。
        String sql="SELECT DISTINCT i.order_id FROM order_item i WHERE i.order_id IN ("+placeholders+") "
                + "AND i.quantity>COALESCE((SELECT SUM(ai.quantity) FROM after_sales_item ai "
                + "JOIN after_sales a ON a.id=ai.after_sales_id WHERE ai.order_item_id=i.id "
                + "AND a.order_id=i.order_id AND a.status<>90),0)";
        return new java.util.HashSet<>(jdbc.queryForList(sql,Long.class,orderIds.toArray()));
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public Optional<OrderItem> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.findByIdForUpdate(id));
    }

    @Override
    public long countByProductId(Long productId) {
        return mapper.selectCountByQuery(
                QueryWrapper.create().where("product_id = ?", productId)
        );
    }

    @Override
    public long countByPackageId(Long packageId) {
        return mapper.selectCountByQuery(
                QueryWrapper.create().where("package_id = ?", packageId)
        );
    }
}
