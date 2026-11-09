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
    @org.apache.ibatis.annotations.ConstructorArgs({
            @org.apache.ibatis.annotations.Arg(column="productId", javaType=Long.class),
            @org.apache.ibatis.annotations.Arg(column="baseUnitId", javaType=Long.class),
            @org.apache.ibatis.annotations.Arg(column="availableBaseQuantity", javaType=BigDecimal.class)
    })
    @Select({"<script>",
            "SELECT b.product_id AS productId, r.base_unit_id AS baseUnitId, SUM(b.base_quantity-b.reserved_base_quantity) AS availableBaseQuantity",
            "FROM inventory_batch_balance b JOIN inventory_receipt_batch r ON r.id=b.batch_id",
            "WHERE b.store_id=#{storeId} AND b.product_id IN",
            "<foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND EXISTS(SELECT 1 FROM inventory_location l WHERE l.id=b.location_id AND l.status=1)",
            "AND b.base_quantity &gt; b.reserved_base_quantity",
            "GROUP BY b.product_id,r.base_unit_id ORDER BY b.product_id,r.base_unit_id",
            "</script>"})
    List<com.agri_system.domain.inventory.vo.SaleableStock> findSaleableStock(@Param("storeId") Long storeId,
                                                                          @Param("productIds") List<Long> productIds);

    @Select("SELECT * FROM inventory_batch_balance WHERE batch_id = #{batchId} AND location_id = #{locationId} FOR UPDATE")
    InventoryBatchBalance findByBatchAndLocationForUpdate(@Param("batchId") Long batchId,
                                                          @Param("locationId") Long locationId);

    @Select("SELECT COALESCE(SUM(base_quantity - reserved_base_quantity), 0) FROM inventory_batch_balance "
            + "WHERE location_id = #{locationId} AND product_id = #{productId}")
    BigDecimal sumAvailableBaseQuantity(@Param("locationId") Long locationId, @Param("productId") Long productId);

    @Select("SELECT base_quantity FROM inventory_batch_balance WHERE store_id=#{storeId} AND product_id=#{productId} ORDER BY id FOR UPDATE")
    List<BigDecimal> quantitiesForUpdate(@Param("storeId") Long storeId, @Param("productId") Long productId);

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
