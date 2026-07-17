package com.agri_system.infrastructure.store.mapper;

import com.agri_system.domain.store.model.Store;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 门店Mapper接口
 * 
 * @author AI Generated
 */
@Mapper
public interface StoreMapper extends BaseMapper<Store> {

    @Select("SELECT * FROM store WHERE id = #{id} LIMIT 1")
    Store selectByIdIncludingDeleted(@Param("id") Long id);

    @Select("SELECT * FROM store WHERE code = #{code} LIMIT 1")
    Store selectByCodeIncludingDeleted(@Param("code") String code);
}






































