package com.agri_system.domain.product.repository;

import com.agri_system.domain.product.model.ProductUnit;

import java.util.List;
import java.util.Optional;

/** SKU 业务单位仓储。 */
public interface ProductUnitRepository {
    void save(ProductUnit unit);

    Optional<ProductUnit> findById(Long id);

    List<ProductUnit> findByProductId(Long productId);

    List<ProductUnit> findEnabledByProductId(Long productId);

    void delete(Long id);

    void deleteByProductId(Long productId);
}
