package com.agri_system.domain.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentity;

import java.util.Optional;

/**
 * 客户身份仓储接口
 * 
 * @author AI Generated
 */
public interface CustomerIdentityRepository {
    /** 仅授权元数据，不加载sessionKey；登录只能写本次真实平台返回的新凭据。 */
    Optional<CustomerIdentity> findById(Long id);
    /** 同样不读取旧凭据，原身份行锁保持至调用事务结束。 */
    Optional<CustomerIdentity> findWechatByAppAndOpenIdForUpdate(String appId, String openId);
    java.util.List<CustomerIdentity> findActiveWechatByCustomerAndApp(Long customerId, String appId);
    
    /**
     * 保存身份。更新必须携带实际读取的authVersion；授权字段变化递增版本，
     * 陈旧快照拒绝，凭据刷新不撤销其他有效会话。治理写入不得绕过本入口。
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






