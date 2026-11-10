package com.agri_system.domain.catalog.query;

import java.util.List;
import java.util.Optional;

/** 公开只读白名单，不能返回业务实体、员工、库存或内部价格。每页取21项用于20项游标。 */
public interface PublicCatalogQuery {
    List<StoreView> stores(Long after);
    List<ProductSummary> products(Long after,String keyword);
    Optional<ProductView> product(Long id);
    record StoreView(String storeId,String name,String phone,String address) {}
    record ProductSummary(String productId,String name,String specification,String manufacturer,String imageUrl) {}
    record ProductView(String productId,String name,String specification,String manufacturer,String registrationNo,String imageUrl,
                       String detailText,String storageConditions,Integer shelfLifeDays) {}
}
