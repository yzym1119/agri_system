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
    public Optional<Order> findByIdForUpdate(Long id) {
        return Optional.ofNullable(mapper.selectOneByQuery(QueryWrapper.create().where("id = ?", id).forUpdate()));
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
        QueryWrapper query = QueryWrapper.create().from(Order.class).as("o").select("o.*")
                .innerJoin("order_delivery").as("od").on("od.order_id = o.id")
                .where("o.order_status IN (5,9)")
                .and("od.delivery_type = 3 AND od.delivery_status = 3")
                .and("od.delivery_at < ?", beforeTime)
                .orderBy("o.id");
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
        return findPage(pageNum, pageSize, keyword, storeId, orderType, deliveryType, orderStatus, startTime, endTime, customerId, null);
    }

    @Override
    public com.mybatisflex.core.paginate.Page<Order> findPage(Integer pageNum, Integer pageSize, String keyword,
            Long storeId, Integer orderType, Integer deliveryType, String orderStatus, String startTime,
            String endTime, Long customerId, Integer deliveryStatus) {
        // 注意：order 是 MySQL 保留关键字，MyBatis-Flex 的 from() 方法接受 Class 类型时会自动处理表名和反引号
        QueryWrapper query = QueryWrapper.create()
                .from(Order.class).as("o").select("o.*")
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
                query.and("o.order_type = ?", 2);
            } else if (deliveryType == 2 || deliveryType == 3) {
                query.and("od.delivery_type = ?", deliveryType);
            } else {
                throw new IllegalArgumentException("配送方式无效");
            }
        }
        if (deliveryStatus != null) query.and("od.delivery_status = ?", deliveryStatus);

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
    public boolean hasRemainingDeliveryQuantity(Long orderId) {
        QueryWrapper query = QueryWrapper.create().select("COUNT(*)")
                .from("order_item_batch_allocation").as("a")
                .innerJoin("order_item").as("i").on("i.id = a.order_item_id")
                .innerJoin(Order.class).as("o").on("o.id = i.order_id AND o.store_id = a.store_id")
                .innerJoin("inventory_movement").as("s").on("s.id = a.source_movement_id")
                .where("o.id = ?", orderId)
                .and("s.movement_type = 'SALE' AND s.base_quantity > 0 AND s.base_quantity = a.allocated_base_quantity")
                .and("s.store_id = a.store_id AND s.product_id = a.product_id AND s.batch_id = a.batch_id AND s.from_location_id = a.location_id")
                .and("s.reference_type = 'ORDER' AND s.reference_id = o.id")
                .and("""
                        a.allocated_base_quantity > COALESCE((
                            SELECT SUM(r.base_quantity) FROM inventory_movement r
                            WHERE r.movement_type = 'RETURN' AND r.reference_type = 'SALE_ALLOCATION'
                              AND r.reference_id = a.id AND r.base_quantity > 0
                              AND r.store_id = a.store_id AND r.product_id = a.product_id AND r.batch_id = a.batch_id
                        ), 0)
                        """);
        return mapper.selectCountByQuery(query) > 0;
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
    public java.util.List<Order> findByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.List.of();
        }
        QueryWrapper query = QueryWrapper.create().in("id", ids);
        return mapper.selectListByQuery(query);
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
