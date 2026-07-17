package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryTransferOrderDetail;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存调拨批次明细 Mapper
 */
@Mapper
public interface InventoryTransferOrderDetailMapper extends BaseMapper<InventoryTransferOrderDetail> {
}
