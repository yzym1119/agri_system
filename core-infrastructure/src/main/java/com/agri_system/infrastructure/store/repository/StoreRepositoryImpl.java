package com.agri_system.infrastructure.store.repository;

import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.infrastructure.store.mapper.StoreMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 门店仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepository {
    
    private final StoreMapper storeMapper;
    
    @Override
    public void save(Store store) {
        if (store.getId() == null) {
            storeMapper.insert(store);
        } else {
            storeMapper.update(store);
        }
    }
    
    @Override
    public Optional<Store> findById(Long id) {
        return Optional.ofNullable(storeMapper.selectOneById(id));
    }
    
    @Override
    public Optional<Store> findByCode(String code) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("code = ?", code);
        return Optional.ofNullable(storeMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public Page<Store> findPage(String code, String name, Integer status, Integer supportPickup,
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
        if (supportPickup != null) {
            queryWrapper.where("support_pickup = ?", supportPickup);
        }
        
        queryWrapper.orderBy("created_at DESC");
        
        return storeMapper.paginate(pageNum, pageSize, queryWrapper);
    }
    
    @Override
    public void delete(Long id) {
        // 逻辑删除：更新 deleted = 1
        Store store = new Store();
        store.setId(id);
        store.setDeleted(1);
        storeMapper.update(store);
    }
}










