package com.agri_system.domain.customer.repository;

import com.agri_system.domain.customer.model.Customer;
import com.mybatisflex.core.paginate.Page;

import java.util.Optional;

/**
 * 客户仓储接口
 * 
 * @author AI Generated
 */
public interface CustomerRepository {
    
    /**
     * 保存客户
     */
    Customer save(Customer customer);
    
    /**
     * 根据ID查询客户
     */
    Optional<Customer> findById(Long id);

    /**
     * 根据ID查询客户（包含已逻辑删除）
     */
    Optional<Customer> findByIdIncludingDeleted(Long id);
    
    /**
     * 根据ID查询客户（带悲观锁）
     * 
     * @param id 客户ID
     * @return 客户
     */
    Optional<Customer> findByIdWithLock(Long id);

    /**
     * 查询所有VIP客户（用于批量降级任务）
     * 
     * @return VIP客户列表
     */
    java.util.List<Customer> findAllVipCustomers();
    
    /**
     * 分页查询客户列表
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param name 客户姓名（模糊查询，可选）
     * @param phone 手机号（模糊查询，可选）
     * @param type 客户类型（可选）
     * @param status 状态（可选）
     * @param bindStaffId 归属员工ID（可选）
     * @return 分页结果
     */
    Page<Customer> findPage(Integer pageNum, Integer pageSize, 
                            String name, String phone, 
                            Integer type, Integer status, Long bindStaffId);
    
    /**
     * 根据手机号查询客户
     * 
     * @param phone 手机号
     * @return 客户
     */
    Optional<Customer> findByPhone(String phone);

    /** 身份开户匹配包括停用/已删除，最多2条；不能把不可用旧档案当无人。 */
    java.util.List<Long> findIdsByPhoneIncludingDeleted(String phone);

    /**
     * 按姓名/编码/手机号关键字查询正常状态客户（收银选客户用，按创建时间倒序）
     *
     * @param keyword 关键字，为空时返回最近的客户
     * @param limit   最多返回条数
     * @return 客户列表
     */
    java.util.List<Customer> findActiveByKeyword(String keyword, int limit);
    
    /**
     * 逻辑删除客户
     *
     * @param id 客户ID
     */
    void logicDelete(Long id);

    /**
     * 统计指定员工的归属客户数量
     */
    long countByBindStaffId(Long staffId);

    /**
     * 查询指定员工的归属客户列表
     */
    java.util.List<Customer> findByBindStaffId(Long staffId);

    /**
     * 查询所有有归属员工的客户
     */
    java.util.List<Customer> findAllWithBindStaff();
}


