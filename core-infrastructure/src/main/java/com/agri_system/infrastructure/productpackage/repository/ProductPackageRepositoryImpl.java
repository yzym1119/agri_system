package com.agri_system.infrastructure.productpackage.repository;

import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.agri_system.infrastructure.productpackage.mapper.ProductPackageMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 套餐仓储实现
 *
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class ProductPackageRepositoryImpl implements ProductPackageRepository {

    private final ProductPackageMapper packageMapper;

    @Override
    public void save(ProductPackage productPackage) {
        if (productPackage.getId() == null) {
            packageMapper.insert(productPackage);
        } else {
            packageMapper.update(productPackage);
        }
    }

    @Override
    public Optional<ProductPackage> findById(Long id) {
        return Optional.ofNullable(packageMapper.selectOneById(id));
    }

    @Override
    public Optional<ProductPackage> findByIdForUpdate(Long id) {
        return Optional.ofNullable(packageMapper.selectOneByQuery(QueryWrapper.create().where("id = ?", id).forUpdate()));
    }

    @Override
    public Optional<ProductPackage> findByCode(String code) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("code = ?", code);
        return Optional.ofNullable(packageMapper.selectOneByQuery(queryWrapper));
    }

    @Override
    public Page<ProductPackage> findPage(String code, String name, Integer status,
                                          int pageNum, int pageSize) {
        QueryWrapper queryWrapper = QueryWrapper.create();

        if (code != null && !code.isEmpty()) {
            queryWrapper.where("code LIKE ?", "%" + code + "%");
        }
        if (name != null && !name.isEmpty()) {
            queryWrapper.where("name LIKE ?", "%" + name + "%");
        }
        if (status != null) {
            queryWrapper.where("status = ?", status);
        }

        // 排除已删除的记录
        queryWrapper.where("status != ?", -1);

        queryWrapper.orderBy("created_at DESC");

        return packageMapper.paginate(pageNum, pageSize, queryWrapper);
    }

    @Override
    public void delete(Long id) {
        // 软删除：更新status为-1
        ProductPackage productPackage = new ProductPackage();
        productPackage.setId(id);
        productPackage.setStatus(-1);
        packageMapper.update(productPackage);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        ProductPackage productPackage = new ProductPackage();
        productPackage.setId(id);
        productPackage.setStatus(status);
        packageMapper.update(productPackage);
    }
}










