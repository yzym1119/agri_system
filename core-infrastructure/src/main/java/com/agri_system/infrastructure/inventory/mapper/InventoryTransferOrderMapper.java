package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryTransferOrder;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 库存调拨单 Mapper
 */
@Mapper
public interface InventoryTransferOrderMapper extends BaseMapper<InventoryTransferOrder> {

    @Select("SELECT transfer_no FROM inventory_transfer WHERE transfer_no LIKE CONCAT(#{prefix}, '%') ORDER BY transfer_no DESC LIMIT 1")
    String findMaxTransferNoByPrefix(@Param("prefix") String prefix);
}
