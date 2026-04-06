package com.agri_system.domain.purchase.repository;

import com.agri_system.domain.purchase.model.Supplier;
import com.mybatisflex.core.paginate.Page;

import java.util.Optional;

/**
 * 供应商仓储接口
 * 
 * @author AI Generated
 */
public interface SupplierRepository {
    
    /**
     * 保存供应商
     */
    void save(Supplier supplier);
    
    /**
     * 根据ID查找供应商
     */
    Optional<Supplier> findById(Long id);
    
    /**
     * 根据编码查找供应商
     */
    Optional<Supplier> findByCode(String code);
    
    /**
     * 分页查询供应商
     */
    Page<Supplier> findPage(String code, String name, Integer status,
                            int pageNum, int pageSize);
    
    /**
     * 逻辑删除供应商
     */
    void delete(Long id);
}

