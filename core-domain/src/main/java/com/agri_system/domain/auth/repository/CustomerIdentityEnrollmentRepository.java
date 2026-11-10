package com.agri_system.domain.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import java.util.Optional;
import java.util.List;

public interface CustomerIdentityEnrollmentRepository {
    /** 在调用方同Flex事务内争用请求/当前微信锚并锁定；不能单独提交PROCESSING。 */
    CustomerIdentityEnrollment acquire(CustomerIdentityEnrollment request);
    Optional<CustomerIdentityEnrollment> findByRequestKey(String key,boolean lock);
    Optional<CustomerIdentityEnrollment> findById(Long id,boolean lock);
    List<CustomerIdentityEnrollment> page(String status,Long before,int limit);
    void finishRegistration(CustomerIdentityEnrollment request);
    /** 先争用全局决定请求键，同事务再改映射及固定最终结果。 */
    void claimDecision(CustomerIdentityEnrollment request);
    void finishDecision(CustomerIdentityEnrollment request);
}
