package com.agri_system.infrastructure.marketing.points.mapper;

import com.agri_system.domain.marketing.points.model.PointsExchangeOrder;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PointsExchangeOrderMapper extends BaseMapper<PointsExchangeOrder> {

    @Select("select * from points_exchange_order where id = #{id} for update")
    PointsExchangeOrder selectByIdWithLock(@Param("id") Long id);

    @Select("select * from points_exchange_order where pickup_code = #{pickupCode}")
    PointsExchangeOrder selectByPickupCode(@Param("pickupCode") String pickupCode);

    @Select("select * from points_exchange_order where pickup_code = #{pickupCode} for update")
    PointsExchangeOrder selectByPickupCodeForUpdate(@Param("pickupCode") String pickupCode);
}

