package com.agri_system.domain.purchase.repository;

import com.agri_system.domain.purchase.model.PurchaseOrder;
import com.mybatisflex.core.paginate.Page;

import java.util.Optional;

/**
 * 采购订单仓储接口
 * 
 * @author AI Generated
 */
public interface PurchaseRepository {
    
    /**
     * 保存采购订单
     */
    void save(PurchaseOrder order);
    
    /**
     * 根据ID查找采购订单
     */
    Optional<PurchaseOrder> findById(Long id);
    
    /**
     * 根据采购单号查找采购订单
     */
    Optional<PurchaseOrder> findByOrderNo(String orderNo);
    
    /**
     * 分页查询采购订单
     */
    Page<PurchaseOrder> findPage(Long storeId, Integer status, String orderNo,
                                 int pageNum, int pageSize);

    Page<PurchaseOrder> findPage(Long storeId, Integer status, String orderNo, String supplierName,
                                 java.time.LocalDateTime createdStartTime, java.time.LocalDateTime createdEndTime,
                                 int pageNum, int pageSize);

    /**
     * 根据前缀查找最大订单号（用于生成流水号）
     */
    String findMaxOrderNoByPrefix(String prefix);

    /**
     * 统计供应商被采购单引用次数
     */
    long countBySupplierId(Long supplierId);
    
    /**
     * 仅更新状态
     */
    void updateStatus(Long id, Integer status);
}
