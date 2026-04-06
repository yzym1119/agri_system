package com.agri_system.infrastructure.product.repository;

import com.agri_system.domain.product.model.ProductUnitConversion;
import com.agri_system.domain.product.repository.ProductUnitConversionRepository;
import com.agri_system.infrastructure.product.mapper.ProductUnitConversionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品单位换算仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class ProductUnitConversionRepositoryImpl implements ProductUnitConversionRepository {
    
    private final ProductUnitConversionMapper conversionMapper;
    
    @Override
    public void save(ProductUnitConversion conversion) {
        if (conversion.getId() == null) {
            conversionMapper.insert(conversion);
        } else {
            conversionMapper.update(conversion);
        }
    }
    
    @Override
    public Optional<ProductUnitConversion> findById(Long id) {
        return Optional.ofNullable(conversionMapper.selectOneById(id));
    }
    
    @Override
    public Optional<ProductUnitConversion> findByProductIdAndUnits(Long productId, String fromUnit, String toUnit) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("product_id = ?", productId)
                .and("from_unit = ?", fromUnit)
                .and("to_unit = ?", toUnit);
        return Optional.ofNullable(conversionMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public List<ProductUnitConversion> findByProductId(Long productId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("product_id = ?", productId);
        return conversionMapper.selectListByQuery(queryWrapper);
    }
    
    @Override
    public void delete(Long id) {
        conversionMapper.deleteById(id);
    }
}

