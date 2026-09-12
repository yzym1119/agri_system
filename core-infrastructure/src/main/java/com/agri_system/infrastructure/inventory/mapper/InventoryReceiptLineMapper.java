package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryReceiptLine;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryReceiptLineMapper extends BaseMapper<InventoryReceiptLine> {
}
