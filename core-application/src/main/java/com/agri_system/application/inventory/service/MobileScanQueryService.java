package com.agri_system.application.inventory.service;

import cn.dev33.satoken.exception.NotLoginException;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.inventory.query.MobileInventoryQuery;
import com.agri_system.domain.inventory.query.MobileInventoryQuery.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 员工查货身份与本店范围编排；不提供库存或成本写入。 */
@Service
@RequiredArgsConstructor
public class MobileScanQueryService {
    private final MobileInventoryQuery queries;
    private final StaffRepository staff;

    public Resolution resolve(String code,String purpose,Long staffId) {
        return queries.resolveInStore(code,purpose,ownStore(staffId));
    }
    public Matches searchProducts(String keyword,int page,Long staffId) {
        return queries.searchProductsInStore(keyword,page,ownStore(staffId));
    }
    public Matches locationProducts(Long locationId,int page,Long staffId) {
        return queries.locationProductsInStore(locationId,page,ownStore(staffId));
    }
    public InventoryView inventory(Long productId,Long batchId,Long locationId,int page,Long staffId) {
        return queries.inventoryInStore(productId,batchId,locationId,page,ownStore(staffId));
    }

    private long ownStore(Long staffId) {
        var actor=staffId==null?null:staff.findIdentityById(staffId).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted())
            throw NotLoginException.newInstance("STAFF",NotLoginException.KICK_OUT,"员工登录已失效",null);
        if(actor.getStoreId()==null||actor.getStoreId()<=0) throw new IllegalStateException("当前员工未分配门店");
        return actor.getStoreId();
    }
}
