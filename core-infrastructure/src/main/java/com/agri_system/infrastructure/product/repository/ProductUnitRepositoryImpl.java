package com.agri_system.infrastructure.product.repository;

import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.repository.ProductUnitRepository;
import com.agri_system.infrastructure.product.mapper.ProductUnitMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductUnitRepositoryImpl implements ProductUnitRepository {
    private final ProductUnitMapper unitMapper;

    @Override
    public void save(ProductUnit unit) {
        if (unit.getId() == null) {
            unitMapper.insert(unit);
        } else {
            unitMapper.update(unit);
        }
    }

    @Override
    public Optional<ProductUnit> findById(Long id) {
        return Optional.ofNullable(unitMapper.selectOneById(id));
    }

    @Override
    public List<ProductUnit> findByProductId(Long productId) {
        QueryWrapper query = QueryWrapper.create()
                .where("product_id = ?", productId)
                .orderBy("id ASC");
        return unitMapper.selectListByQuery(query);
    }

    @Override
    public List<ProductUnit> findEnabledByProductId(Long productId) {
        QueryWrapper query = QueryWrapper.create()
                .where("product_id = ?", productId)
                .and("status = 1")
                .orderBy("id ASC");
        return unitMapper.selectListByQuery(query);
    }

    @Override
    public void delete(Long id) {
        unitMapper.deleteById(id);
    }

    @Override
    public void deleteByProductId(Long productId) {
        unitMapper.deleteByQuery(QueryWrapper.create().where("product_id = ?", productId));
    }
}
