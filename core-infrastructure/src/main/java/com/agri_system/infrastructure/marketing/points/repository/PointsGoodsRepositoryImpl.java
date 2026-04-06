package com.agri_system.infrastructure.marketing.points.repository;

import com.agri_system.domain.marketing.points.model.PointsGoods;
import com.agri_system.domain.marketing.points.repository.PointsGoodsRepository;
import com.agri_system.infrastructure.marketing.points.mapper.PointsGoodsMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PointsGoodsRepositoryImpl implements PointsGoodsRepository {

    private final PointsGoodsMapper mapper;

    @Override
    public PointsGoods save(PointsGoods goods) {
        if (goods.getId() == null) {
            mapper.insert(goods);
        } else {
            mapper.update(goods);
        }
        return goods;
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public Optional<PointsGoods> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public Optional<PointsGoods> findByIdWithLock(Long id) {
        return Optional.ofNullable(mapper.selectByIdWithLock(id));
    }

    @Override
    public List<PointsGoods> findAllOnSale() {
        QueryWrapper query = QueryWrapper.create()
                .where("status = ?", 1)
                .orderBy("sort_order ASC", "id DESC");
        return mapper.selectListByQuery(query);
    }

    @Override
    public List<PointsGoods> findAll() {
        QueryWrapper query = QueryWrapper.create()
                .orderBy("sort_order ASC", "id DESC");
        return mapper.selectListByQuery(query);
    }

    @Override
    public List<PointsGoods> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper query = QueryWrapper.create()
                .where("id in (?)", ids);
        return mapper.selectListByQuery(query);
    }
}

