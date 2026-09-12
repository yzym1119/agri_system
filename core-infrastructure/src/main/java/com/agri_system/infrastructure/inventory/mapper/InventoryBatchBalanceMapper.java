package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface InventoryBatchBalanceMapper extends BaseMapper<InventoryBatchBalance> {
    @Select("SELECT * FROM inventory_batch_balance WHERE batch_id = #{batchId} AND location_id = #{locationId} FOR UPDATE")
    InventoryBatchBalance findByBatchAndLocationForUpdate(@Param("batchId") Long batchId,
                                                          @Param("locationId") Long locationId);

    @Select("SELECT COALESCE(SUM(base_quantity - reserved_base_quantity), 0) FROM inventory_batch_balance "
            + "WHERE location_id = #{locationId} AND product_id = #{productId}")
    BigDecimal sumAvailableBaseQuantity(@Param("locationId") Long locationId, @Param("productId") Long productId);

    @Select("SELECT COALESCE(SUM(base_quantity), 0) FROM inventory_batch_balance "
            + "WHERE store_id = #{storeId} AND product_id = #{productId}")
    BigDecimal sumBaseQuantityByStoreAndProduct(@Param("storeId") Long storeId, @Param("productId") Long productId);
}
