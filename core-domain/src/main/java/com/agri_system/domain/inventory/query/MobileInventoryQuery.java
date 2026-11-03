package com.agri_system.domain.inventory.query;

import java.util.List;

/** 员工查货专用只读聚合；调用方必须先验证真实员工身份并传入其门店。无成本或写入能力。 */
public interface MobileInventoryQuery {
    Resolution resolveInStore(String code,String purpose,Long storeId);
    Matches searchProductsInStore(String keyword,int page,Long storeId);
    Matches locationProductsInStore(Long locationId,int page,Long storeId);
    InventoryView inventoryInStore(Long productId,Long batchId,Long locationId,int page,Long storeId);
    public record Match(String type,String id,String productId,String productCode,String name,String unitId,String unitName,boolean enabled,String code) {}
    public record Resolution(String storeId,String type,List<Match> matches,boolean hasMore) {}
    public record Matches(String storeId,int page,List<Match> matches,boolean hasMore) {}
    public record InventoryView(String storeId,String productId,String productCode,String productName,boolean productEnabled,String mainUnitName,
                                String quantity,String unreservedQuantity,int page,long totalRows,List<Stock> rows) {}
    public record Stock(String batchId,String locationId,String internalBatchNo,String supplierBatchNo,String locationName,String locationCode,boolean locationEnabled,
                        String baseQuantity,String unreservedBaseQuantity,String baseUnitName,String originalUnitName,String originalQuantity,String productionDate,String expiryDate) {}
}
