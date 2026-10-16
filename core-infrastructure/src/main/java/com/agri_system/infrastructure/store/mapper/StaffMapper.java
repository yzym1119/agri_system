package com.agri_system.infrastructure.store.mapper;

import com.agri_system.domain.store.model.Staff;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 员工Mapper接口（用于店长校验）
 * 注意：完整的 Staff 模块将在后续实现
 * 
 * @author AI Generated
 */
@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
    @Select("SELECT * FROM staff WHERE id = #{id} AND deleted = 0")
    Staff selectIdentityById(@Param("id") Long id);

    @Update("UPDATE staff SET store_id=#{storeId} WHERE id=#{staffId} AND deleted=0")
    int transferStore(@Param("staffId") Long staffId, @Param("storeId") Long storeId);
}







































