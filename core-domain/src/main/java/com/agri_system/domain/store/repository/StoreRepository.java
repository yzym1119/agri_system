package com.agri_system.domain.store.repository;

import com.agri_system.domain.store.model.Store;
import com.mybatisflex.core.paginate.Page;

import java.util.Optional;

/**
 * 门店仓储接口
 * 
 * @author AI Generated
 */
public interface StoreRepository {
    
    /**
     * 保存门店
     */
    void save(Store store);
    
    /**
     * 根据ID查找门店
     */
    Optional<Store> findById(Long id);
    
    /**
     * 根据编码查找门店
     */
    Optional<Store> findByCode(String code);
    
    /**
     * 分页查询门店
     */
    Page<Store> findPage(String code, String name, Integer status, Integer supportPickup,
                         int pageNum, int pageSize);
    
    /**
     * 逻辑删除门店
     */
    void delete(Long id);
}










