package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderItem;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单明细Mapper接口
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT * FROM order_item WHERE id = #{id} FOR UPDATE")
    OrderItem findByIdForUpdate(@Param("id") Long id);

    /**
     * 查询商品销售速度摘要（近N天）
     * 返回每个商品的总销量和日均销量
     */
    @Select("<script>"
            + "SELECT oi.product_id AS productId,"
            + "  p.name AS productName,"
            + "  p.code AS productCode,"
            + "  p.main_unit AS mainUnit,"
            + "  p.min_stock AS currentMinStock,"
            + "  p.max_stock AS currentMaxStock,"
            + "  p.reorder_quantity AS currentReorderQuantity,"
            + "  SUM(oi.quantity) AS totalSold,"
            + "  ROUND(SUM(oi.quantity) / #{days}, 2) AS avgDailySales,"
            + "  COUNT(DISTINCT o.id) AS orderCount"
            + " FROM order_item oi"
            + " JOIN `order` o ON oi.order_id = o.id"
            + " JOIN product p ON oi.product_id = p.id"
            + " WHERE o.order_status IN (2,3,4,5,6,9)"
            + "   AND oi.item_type = 1"
            + "   AND oi.product_id IS NOT NULL"
            + "   AND o.created_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)"
            + "   <if test='storeId != null'> AND o.store_id = #{storeId} </if>"
            + " GROUP BY oi.product_id, p.name, p.code, p.min_stock, p.max_stock, p.reorder_quantity"
            + " HAVING totalSold > 0"
            + " ORDER BY avgDailySales DESC"
            + "</script>")
    List<Map<String, Object>> selectProductSalesVelocity(
            @Param("days") int days,
            @Param("storeId") Long storeId
    );
}





































