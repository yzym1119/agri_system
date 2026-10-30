package com.agri_system.application.purchase.dto.vo;

import java.util.List;

/** 手机收货只读资料：编号和数量精确传输，单位粒度来自服务端。 */
public final class MobileReceivingVO {
    private MobileReceivingVO() {}
    public record Orders(String storeId,int page,boolean hasMore,List<Order> orders) {}
    public record Order(String id,String orderNo,String supplierName) {}
    public record Detail(String id,String storeId,String orderNo,String supplierName,List<Item> items) {}
    public record Item(String id,String productId,String productCode,String productName,String orderedQuantity,String receivedBaseQuantity,
                       String remainingBaseQuantity,String orderedUnit,String baseUnitName,boolean allowFractionalBaseQuantity,List<Unit> units,List<String> scanCodes) {}
    public record Unit(String id,String name,String baseQuantityPerUnit) {}
}
