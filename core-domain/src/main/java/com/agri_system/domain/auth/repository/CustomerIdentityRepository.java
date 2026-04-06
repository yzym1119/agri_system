package com.agri_system.domain.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentity;

import java.util.Optional;

/**
 * 客户身份仓储接口
 * 
 * @author AI Generated
 */
public interface CustomerIdentityRepository {
    
    /**
     * 保存身份
     */
    CustomerIdentity save(CustomerIdentity identity);
    
    /**
     * 根据身份类型和身份值查询
     * 
     * @param identityType 身份类型
     * @param identityValue 身份值
     * @return 身份
     */
    Optional<CustomerIdentity> findByTypeAndValue(String identityType, String identityValue);
    
    /**
     * 根据客户ID和身份类型查询
     * 
     * @param customerId 客户ID
     * @param identityType 身份类型
     * @return 身份
     */
    Optional<CustomerIdentity> findByCustomerIdAndType(Long customerId, String identityType);
}






