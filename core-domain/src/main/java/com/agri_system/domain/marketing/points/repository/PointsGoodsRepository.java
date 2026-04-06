package com.agri_system.domain.marketing.points.repository;

import com.agri_system.domain.marketing.points.model.PointsGoods;

import java.util.List;
import java.util.Optional;

public interface PointsGoodsRepository {

    PointsGoods save(PointsGoods goods);

    void deleteById(Long id);

    Optional<PointsGoods> findById(Long id);

    Optional<PointsGoods> findByIdWithLock(Long id);

    List<PointsGoods> findAllOnSale();

    List<PointsGoods> findAll();

    List<PointsGoods> findByIds(List<Long> ids);
}

