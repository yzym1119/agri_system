package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryReceipt;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryReceiptMapper extends BaseMapper<InventoryReceipt> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM inventory_receipt WHERE request_key=#{key} AND store_id=#{storeId} AND receiver_id=#{receiverId}")
    InventoryReceipt findByRequestKeyAndOwner(@org.apache.ibatis.annotations.Param("key") String key,
            @org.apache.ibatis.annotations.Param("storeId") Long storeId,@org.apache.ibatis.annotations.Param("receiverId") Long receiverId);
}
