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

    // 与CustomerPhoneNumbers的已知大陆别名规则一致；历史值只读，不排除停用/删除。
    @Select("SELECT id FROM customer WHERE REPLACE(REPLACE(phone,' ',''),'-','') "
            + "IN (#{phone},CONCAT('86',#{phone}),CONCAT('+86',#{phone})) ORDER BY id LIMIT 2")
    java.util.List<Long> selectIdsByPhoneIncludingDeleted(@Param("phone") String phone);

    @Select("SELECT * FROM customer WHERE id = #{id} LIMIT 1")
    Customer selectByIdIncludingDeleted(@Param("id") Long id);
}






