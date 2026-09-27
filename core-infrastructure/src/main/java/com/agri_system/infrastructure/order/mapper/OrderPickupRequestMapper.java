package com.agri_system.infrastructure.order.mapper;

import com.agri_system.domain.order.model.OrderPickupRequest;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库房取货请求 Mapper。
 */
@Mapper
public interface OrderPickupRequestMapper extends BaseMapper<OrderPickupRequest> {
}
