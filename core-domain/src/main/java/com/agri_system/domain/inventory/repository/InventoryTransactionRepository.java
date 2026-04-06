package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryTransaction;
import com.mybatisflex.core.paginate.Page;

/**
 * 库存流水仓储接口
 * 
 * @author AI Generated
 */
public interface InventoryTransactionRepository {
    
    /**
     * 保存流水记录
     */
    void save(InventoryTransaction transaction);
    
    /**
     * 分页查询库存流水
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param storeId 门店ID（可选）
     * @param productName 商品名称（可选，模糊查询）
     * @param type 流水类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 分页结果
     */
    Page<InventoryTransaction> findPage(Integer pageNum, Integer pageSize, 
                                       Long storeId, String productName, 
                                       Integer type, String startTime, String endTime);
}

