package com.agri_system.infrastructure.order.repository;

import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 订单仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {
    
    private final OrderMapper mapper;
    
    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            mapper.insert(order);
        } else {
            mapper.update(order);
        }
        return order;
    }
    
    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }
    
    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        Order order = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("order_no = ?", orderNo)
        );
        return Optional.ofNullable(order);
    }
    
    @Override
    public Optional<Order> findByPickupCode(String pickupCode) {
        Order order = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("pickup_code = ?", pickupCode)
        );
        return Optional.ofNullable(order);
    }

    @Override
    public BigDecimal sumPayAmountByStaffAndMonth(Long staffId, String month) {
        QueryWrapper query = QueryWrapper.create()
                .select("COALESCE(SUM(pay_amount),0) AS total")
                .where("staff_id = ?", staffId)
                .and("DATE_FORMAT(created_at,'%Y-%m') = ?", month);
        BigDecimal result = mapper.selectOneByQueryAs(query, BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public java.util.List<Order> findShippedOrdersBefore(java.time.LocalDateTime beforeTime) {
        QueryWrapper query = QueryWrapper.create()
                .where("order_status = ?", 5) // 已发货
                .and("updated_at < ?", beforeTime);
        return mapper.selectListByQuery(query);
    }
    
    @Override
    public com.mybatisflex.core.paginate.Page<Order> findPage(
            Integer pageNum,
            Integer pageSize,
            String keyword,
            Long storeId,
            Integer orderType,
            Integer deliveryType,
            String orderStatus,
            String startTime,
            String endTime,
            Long customerId) {
        // 注意：order 是 MySQL 保留关键字，MyBatis-Flex 的 from() 方法接受 Class 类型时会自动处理表名和反引号
        QueryWrapper query = QueryWrapper.create()
                .from(Order.class).as("o")
                .leftJoin("store").as("s").on("o.store_id = s.id")
                .leftJoin("customer").as("c").on("o.customer_id = c.id")
                .leftJoin("order_delivery").as("od").on("o.id = od.order_id");
        
        // 关键词搜索（订单号/手机号/收货人）
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.and("(o.order_no LIKE ? OR o.receiver_phone LIKE ? OR o.receiver_name LIKE ? OR c.phone LIKE ?)",
                    "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%");
        }
        
        if (storeId != null) {
            query.and("o.store_id = ?", storeId);
        }
        
        if (orderType != null) {
            query.and("o.order_type = ?", orderType);
        }
        
        if (deliveryType != null) {
            if (deliveryType == 1) {
                query.and("o.order_type = ?", 2); // 自提
            } else if (deliveryType == 2) {
                query.and("o.order_type = ?", 3)
                        .and("od.delivery_type = ?", 2);
            } else if (deliveryType == 3) {
                query.and("o.order_type = ?", 3)
                        .and("(od.delivery_type = ? OR od.id IS NULL)", 3);
            }
        }
        
        if (orderStatus != null && !orderStatus.trim().isEmpty()) {
            // 支持多状态查询，用逗号分隔，如：7,8,9
            if (orderStatus.contains(",")) {
                String[] statuses = orderStatus.split(",");
                query.and("o.order_status IN (" + String.join(",", java.util.Arrays.stream(statuses).map(s -> "?").collect(java.util.stream.Collectors.toList())) + ")",
                        (Object[]) java.util.Arrays.stream(statuses).map(Integer::parseInt).toArray());
            } else {
                query.and("o.order_status = ?", Integer.parseInt(orderStatus.trim()));
            }
        }
        
        // 过滤客户（仅当传入时生效）
        if (customerId != null) {
            query.and("o.customer_id = ?", customerId);
        }

        if (startTime != null && !startTime.trim().isEmpty()) {
            query.and("o.created_at >= ?", java.time.LocalDateTime.parse(startTime));
        }
        
        if (endTime != null && !endTime.trim().isEmpty()) {
            query.and("o.created_at <= ?", java.time.LocalDateTime.parse(endTime));
        }
        
        query.orderBy("o.created_at DESC");
        
        com.mybatisflex.core.paginate.Page<Order> page = new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }

    @Override
    public long countActiveOrdersByCustomerId(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .and("order_status IN (1, 2, 3, 4, 5, 9)");
        return mapper.selectCountByQuery(query);
    }

    @Override
    public java.math.BigDecimal sumPayAmountByStaffAndDateRange(Long staffId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        QueryWrapper query = QueryWrapper.create()
                .select("COALESCE(SUM(pay_amount), 0) AS total")
                .where("staff_id = ?", staffId)
                .and("created_at >= ?", start)
                .and("created_at <= ?", end)
                .and("order_status IN (2, 3, 4, 5, 6)");
        Object result = mapper.selectObjectByQuery(query);
        if (result == null) return java.math.BigDecimal.ZERO;
        if (result instanceof java.math.BigDecimal) return (java.math.BigDecimal) result;
        return new java.math.BigDecimal(result.toString());
    }

    @Override
    public Integer countByStaffAndDateRange(Long staffId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        // 仅统计已支付的有效订单
        QueryWrapper query = QueryWrapper.create()
                .where("staff_id = ?", staffId)
                .and("created_at >= ?", start)
                .and("created_at <= ?", end)
                .and("order_status IN (2, 3, 4, 5, 6)");
        return (int) mapper.selectCountByQuery(query);
    }
}
