package com.agri_system.infrastructure.inventory.mapper;

import com.agri_system.domain.inventory.model.InventoryBatchBalance;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @Select("SELECT * FROM inventory_batch_balance WHERE location_id = #{locationId} AND product_id = #{productId} "
            + "ORDER BY batch_id")
    List<InventoryBatchBalance> findByLocationAndProduct(@Param("locationId") Long locationId,
                                                         @Param("productId") Long productId);

    /** 门店可用库存（base - reserved）按商品分组，一次查询覆盖全部商品。 */
    @Select("SELECT product_id AS productId, COALESCE(SUM(base_quantity - reserved_base_quantity), 0) AS availableBaseQuantity "
            + "FROM inventory_batch_balance WHERE store_id = #{storeId} GROUP BY product_id")
    List<Map<String, Object>> sumAvailableBaseQuantityGroupedByProduct(@Param("storeId") Long storeId);

    /** 门店可用库存（base - reserved）按批次分组，一次查询覆盖全部批次。 */
    @Select("SELECT batch_id AS batchId, COALESCE(SUM(base_quantity - reserved_base_quantity), 0) AS availableBaseQuantity "
            + "FROM inventory_batch_balance WHERE store_id = #{storeId} GROUP BY batch_id")
    List<Map<String, Object>> sumAvailableBaseQuantityGroupedByBatch(@Param("storeId") Long storeId);
}
