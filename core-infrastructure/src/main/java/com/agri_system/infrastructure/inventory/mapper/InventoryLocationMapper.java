package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryLocation;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLocationMapper extends BaseMapper<InventoryLocation> {
    @org.apache.ibatis.annotations.Insert("INSERT INTO inventory_location(store_id,location_type,name,status) VALUES(#{storeId},'STORE_BACKROOM','门店后仓',1) ON DUPLICATE KEY UPDATE id=id")
    void ensureBackroom(@org.apache.ibatis.annotations.Param("storeId") Long storeId);

    @org.apache.ibatis.annotations.Select("SELECT * FROM inventory_location WHERE store_id=#{storeId} AND location_type='STORE_BACKROOM' AND code IS NULL")
    InventoryLocation defaultBackroom(@org.apache.ibatis.annotations.Param("storeId") Long storeId);
    @org.apache.ibatis.annotations.Select("SELECT * FROM inventory_location WHERE store_id=#{storeId} AND location_type='STORE_BACKROOM' AND code IS NULL FOR UPDATE")
    InventoryLocation defaultBackroomForUpdate(@org.apache.ibatis.annotations.Param("storeId") Long storeId);


}
