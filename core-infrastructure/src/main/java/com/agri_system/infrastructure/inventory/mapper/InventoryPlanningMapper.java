package com.agri_system.infrastructure.inventory.mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryPlanningMapper {
    @Select("""
        <script>
        SELECT b.product_id AS productId,p.main_unit AS mainUnit,u.base_quantity_per_unit AS mainUnitFactor,
          CASE WHEN u.base_quantity_per_unit &gt; 0
            AND SUM(CASE WHEN b.base_quantity IS NULL OR b.reserved_base_quantity IS NULL
                OR b.base_quantity &lt; 0 OR b.reserved_base_quantity &lt; 0 OR b.base_quantity &lt; b.reserved_base_quantity THEN 1 ELSE 0 END)=0
            THEN SUM(b.base_quantity-b.reserved_base_quantity)/u.base_quantity_per_unit ELSE NULL END AS currentStock
        FROM inventory_batch_balance b JOIN product p ON p.id=b.product_id
        LEFT JOIN product_unit u ON u.product_id=p.id AND u.unit_name=p.main_unit
        WHERE p.deleted=0
        <if test="storeId != null">AND b.store_id=#{storeId}</if>
        <if test="tenants != null">AND b.store_id IN <foreach collection="tenants" item="tenant" open="(" separator="," close=")">#{tenant}</foreach></if>
        GROUP BY b.product_id,p.main_unit,u.base_quantity_per_unit
        </script>
        """)
    List<Map<String,Object>> stocks(@Param("storeId") Long storeId,@Param("tenants") Object[] tenants);

    @Select("""
        <script>
        SELECT oi.product_id AS productId,p.name AS productName,p.code AS productCode,p.main_unit AS mainUnit,
          p.min_stock AS currentMinStock,p.max_stock AS currentMaxStock,p.reorder_quantity AS currentReorderQuantity,
          SUM(CASE WHEN sale.base_quantity_per_unit &gt; 0 AND main.base_quantity_per_unit &gt; 0 AND oi.quantity &gt; 0 THEN 0 ELSE 1 END) AS unresolvedUnitCount,
          CASE WHEN SUM(CASE WHEN sale.base_quantity_per_unit &gt; 0 AND main.base_quantity_per_unit &gt; 0 AND oi.quantity &gt; 0 THEN 0 ELSE 1 END)=0
            THEN SUM(oi.quantity*sale.base_quantity_per_unit/main.base_quantity_per_unit) ELSE NULL END AS totalSold,
          CASE WHEN SUM(CASE WHEN sale.base_quantity_per_unit &gt; 0 AND main.base_quantity_per_unit &gt; 0 AND oi.quantity &gt; 0 THEN 0 ELSE 1 END)=0
            THEN ROUND(SUM(oi.quantity*sale.base_quantity_per_unit/main.base_quantity_per_unit)/#{days},2) ELSE NULL END AS avgDailySales,
          COUNT(DISTINCT o.id) AS orderCount
        FROM order_item oi JOIN `order` o ON o.id=oi.order_id JOIN product p ON p.id=oi.product_id
        LEFT JOIN product_unit sale ON sale.product_id=p.id AND sale.unit_name=oi.unit
        LEFT JOIN product_unit main ON main.product_id=p.id AND main.unit_name=p.main_unit
        WHERE o.order_status IN (2,3,4,5,6,9) AND oi.item_type=1 AND oi.product_id IS NOT NULL
          AND o.created_at &gt;= DATE_SUB(NOW(),INTERVAL #{days} DAY) AND o.deleted=0 AND p.deleted=0
        <if test="storeId != null">AND o.store_id=#{storeId}</if>
        <if test="tenants != null">AND o.store_id IN <foreach collection="tenants" item="tenant" open="(" separator="," close=")">#{tenant}</foreach></if>
        GROUP BY oi.product_id,p.name,p.code,p.main_unit,p.min_stock,p.max_stock,p.reorder_quantity,main.base_quantity_per_unit
        ORDER BY avgDailySales DESC
        </script>
        """)
    List<Map<String,Object>> salesVelocity(@Param("days") int days,@Param("storeId") Long storeId,@Param("tenants") Object[] tenants);
}
