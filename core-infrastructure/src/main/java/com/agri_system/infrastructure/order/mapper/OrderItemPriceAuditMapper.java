package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderItemPriceAudit;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单行价格审计事实 Mapper。
 *
 * <p>表为追加写（V120 触发器禁止更新/删除），查询按写入顺序返回。</p>
 */
@Mapper
public interface OrderItemPriceAuditMapper extends BaseMapper<OrderItemPriceAudit> {

    @Select("SELECT * FROM order_item_price_audit WHERE order_item_id = #{orderItemId} ORDER BY id")
    List<OrderItemPriceAudit> findByOrderItemId(@Param("orderItemId") Long orderItemId);
}
