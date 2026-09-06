package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryBalanceFeatureStatusMapper extends BaseMapper<InventoryBalanceFeatureStatus> {
    @Select("SELECT * FROM inventory_balance_feature_status WHERE store_id = #{storeId} AND product_id = #{productId}")
    InventoryBalanceFeatureStatus findByStoreAndProduct(@Param("storeId") Long storeId, @Param("productId") Long productId);
}
