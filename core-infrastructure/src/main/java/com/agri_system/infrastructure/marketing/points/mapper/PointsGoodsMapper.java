package com.agri_system.infrastructure.marketing.points.mapper;

import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PointsGoodsMapper extends BaseMapper<PointsGoods> {

    @Select("select * from points_goods where id = #{id} for update")
    PointsGoods selectByIdWithLock(@Param("id") Long id);
}

