package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.Order;

import java.util.Optional;

/**
 * 订单仓储接口
 * 
 * @author AI Generated
 */
public interface OrderRepository {
    
    /**
     * 保存订单
     */
    Order save(Order order);
    
    /**
     * 根据ID查询订单
     */
    Optional<Order> findById(Long id);
    
    /**
     * 根据订单号查询订单
     */
    Optional<Order> findByOrderNo(String orderNo);
    
    /**
     * 根据自提码查询订单
     */
    Optional<Order> findByPickupCode(String pickupCode);

    java.math.BigDecimal sumPayAmountByStaffAndMonth(Long staffId, String month);

    /**
     * 查询已发货超过指定时间的订单
     * 
     * @param beforeTime 时间阈值（查询此时间之前发货的订单）
     * @return 订单列表
     */
    java.util.List<Order> findShippedOrdersBefore(java.time.LocalDateTime beforeTime);
    
    /**
     * 分页查询订单
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param keyword 关键词（订单号/手机号/收货人）
     * @param storeId 门店ID
     * @param customerId 客户ID（可选）
     * @param orderType 订单类型
     * @param deliveryType 配送方式
     * @param orderStatus 订单状态（支持单状态或多状态，多状态用逗号分隔，如：7,8,9）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    com.mybatisflex.core.paginate.Page<Order> findPage(
            Integer pageNum,
            Integer pageSize,
            String keyword,
            Long storeId,
            Integer orderType,
            Integer deliveryType,
            String orderStatus,
            String startTime,
            String endTime,
            Long customerId
    );
}



