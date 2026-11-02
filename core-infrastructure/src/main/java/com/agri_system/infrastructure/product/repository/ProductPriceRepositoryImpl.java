package com.agri_system.infrastructure.product.repository;

import com.agri_system.domain.product.model.ProductPrice;
import com.agri_system.domain.product.repository.ProductPriceRepository;
import com.agri_system.infrastructure.product.mapper.ProductPriceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品价格仓储实现
 *
 * @deprecated product_price 已归档只读（V130 停用多维定价），save/update/delete 无应用层调用方，禁止新增。
 * @author AI Generated
 */
@Deprecated
@Repository
@RequiredArgsConstructor
public class ProductPriceRepositoryImpl implements ProductPriceRepository {
    
    private final ProductPriceMapper priceMapper;
    
    @Override
    public void save(ProductPrice price) {
        if (price.getId() == null) {
            priceMapper.insert(price);
        } else {
            priceMapper.update(price);
        }
    }
    
    @Override
    public Optional<ProductPrice> findById(Long id) {
        return Optional.ofNullable(priceMapper.selectOneById(id));
    }
    
    @Override
    public List<ProductPrice> findByProductId(Long productId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("product_id = ?", productId)
                .orderBy("created_at DESC, id DESC");
        return priceMapper.selectListByQuery(queryWrapper);
    }
    
    @Override
    public void delete(Long id) {
        priceMapper.deleteById(id);
    }
    
    @Override
    public long countActiveByProductId(Long productId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("product_id = ?", productId)
                .and("status = 1");
        return priceMapper.selectCountByQuery(queryWrapper);
    }
}

