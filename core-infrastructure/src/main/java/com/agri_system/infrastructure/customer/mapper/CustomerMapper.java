package com.agri_system.infrastructure.customer.mapper;

import com.agri_system.domain.customer.model.Customer;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    
    /**
     * 根据ID查询客户（带悲观锁）
     * 
     * @param id 客户ID
     * @return 客户
     */
    @Select("SELECT * FROM customer WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Customer selectByIdWithLock(@Param("id") Long id);
}







