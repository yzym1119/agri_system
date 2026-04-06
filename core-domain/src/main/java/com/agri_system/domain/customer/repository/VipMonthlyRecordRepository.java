package com.agri_system.domain.customer.repository;

import com.agri_system.domain.customer.model.VipMonthlyRecord;

import java.util.Optional;

/**
 * VIP每月权益发放记录仓储接口
 * 
 * @author AI Generated
 */
public interface VipMonthlyRecordRepository {
    
    /**
     * 保存记录
     */
    VipMonthlyRecord save(VipMonthlyRecord record);
    
    /**
     * 根据客户ID和月份查询记录（用于幂等性检查）
     * 
     * @param customerId 客户ID
     * @param yearMonth 月份（格式：2023-10）
     * @return 记录
     */
    Optional<VipMonthlyRecord> findByCustomerIdAndYearMonth(Long customerId, String yearMonth);
    
    /**
     * 分页查询客户权益发放记录
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param customerId 客户ID
     * @return 分页结果
     */
    com.mybatisflex.core.paginate.Page<VipMonthlyRecord> findPage(Integer pageNum, Integer pageSize, Long customerId);
}







