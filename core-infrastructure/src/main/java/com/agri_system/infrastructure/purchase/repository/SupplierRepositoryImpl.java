package com.agri_system.infrastructure.purchase.repository;

import com.agri_system.domain.purchase.model.Supplier;
import com.agri_system.domain.purchase.repository.SupplierRepository;
import com.agri_system.infrastructure.purchase.mapper.SupplierMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 供应商仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class SupplierRepositoryImpl implements SupplierRepository {
    
    private final SupplierMapper supplierMapper;
    
    @Override
    public void save(Supplier supplier) {
        if (supplier.getId() == null) {
            supplierMapper.insert(supplier);
        } else {
            supplierMapper.update(supplier);
        }
    }
    
    @Override
    public Optional<Supplier> findById(Long id) {
        return Optional.ofNullable(supplierMapper.selectOneById(id));
    }
    
    @Override
    public Optional<Supplier> findByCode(String code) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("code = ?", code);
        return Optional.ofNullable(supplierMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public Page<Supplier> findPage(String code, String name, Integer status,
                                   int pageNum, int pageSize) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        
        if (code != null && !code.trim().isEmpty()) {
            queryWrapper.where("code LIKE ?", "%" + code + "%");
        }
        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.where("name LIKE ?", "%" + name + "%");
        }
        if (status != null) {
            queryWrapper.where("status = ?", status);
        }
        
        queryWrapper.orderBy("created_at DESC");
        
        return supplierMapper.paginate(pageNum, pageSize, queryWrapper);
    }
    
    @Override
    public void delete(Long id) {
        supplierMapper.deleteById(id);
    }
}

