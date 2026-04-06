package com.agri_system.domain.customer.repository;

import com.agri_system.domain.customer.model.CustomerBenefitLog;

/**
 * 客户权益流水仓储接口
 * 
 * @author AI Generated
 */
public interface CustomerBenefitLogRepository {
    
    /**
     * 保存权益流水记录
     */
    CustomerBenefitLog save(CustomerBenefitLog log);
    
    /**
     * 根据客户ID分页查询权益流水
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param customerId 客户ID
     * @return 分页结果
     */
    com.mybatisflex.core.paginate.Page<CustomerBenefitLog> findPage(Integer pageNum, Integer pageSize, Long customerId);
}









