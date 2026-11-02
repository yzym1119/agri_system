package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 订单明细仓储接口
 * 
 * @author AI Generated
 */
public interface OrderItemRepository {
    
    /**
     * 保存订单明细
     */
    OrderItem save(OrderItem item);
    
    /**
     * 批量保存订单明细
     */
    void saveAll(List<OrderItem> items);
    
    /**
     * 根据订单ID查询所有明细
     */
    List<OrderItem> findByOrderId(Long orderId);

    /** 当前分页整单中仍有未被有效售后占用数量的订单；包含零元物料。 */
    java.util.Set<Long> findOrderIdsWithReturnableItems(List<Long> orderIds);
    
    /**
     * 根据ID查询明细
     */
    Optional<OrderItem> findById(Long id);

    Optional<OrderItem> findByIdForUpdate(Long id);

    /**
     * 仅刷新已核实批次归属的展示快照三列，不保存传入整行或改写历史成本。
     * 在调用方既有出库事务中执行，不新增锁或独立事务；返回实际更新行数。
     */
    int updateBatchCostSnapshot(Long id, Long baseUnitId, BigDecimal baseQuantity, BigDecimal unitCost);


    /**
     * 统计商品被订单明细引用次数
     */
    long countByProductId(Long productId);

    /**
     * 统计套餐被订单明细引用次数
     */
    long countByPackageId(Long packageId);
}






