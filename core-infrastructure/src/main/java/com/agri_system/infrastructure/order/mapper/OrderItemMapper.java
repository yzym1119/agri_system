package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderItem;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 仅更新订单行批次成本展示快照三列（base_unit_id/base_quantity/unit_cost）。
     *
     * <p>定向更新，绝不触碰 cost_price_snapshot 等 legacy 字段；仅批次归属成本为 ACTUAL 时调用，
     * 未激活 SKU 永不写入。</p>
     */
    @Update("UPDATE order_item SET base_unit_id = #{baseUnitId,jdbcType=BIGINT},"
            + " base_quantity = #{baseQuantity,jdbcType=DECIMAL},"
            + " unit_cost = #{unitCost,jdbcType=DECIMAL}"
            + " WHERE id = #{id,jdbcType=BIGINT}")
    int updateBatchCostSnapshot(@Param("id") Long id,
                                @Param("baseUnitId") Long baseUnitId,
                                @Param("baseQuantity") BigDecimal baseQuantity,
                                @Param("unitCost") BigDecimal unitCost);

}
