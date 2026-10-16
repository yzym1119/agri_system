package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.productpackage.dto.vo.PackagePriceQuote;
import com.agri_system.application.productpackage.service.PackagePricingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;

class PackageSaleLockOrderTest {
    @Test
    void reversedCartLocksPackagesInStableOrderButPreservesCartMaterialOrder() {
        var pricing=Mockito.mock(PackagePricingService.class);
        var service=Mockito.mock(OrderApplicationService.class,Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service,"packagePricingService",pricing);
        List<Long> lockOrder=new ArrayList<>();
        Mockito.when(pricing.quoteForSale(anyLong(),isNull(),eq(1L),eq(BigDecimal.ONE),isNull())).thenAnswer(call->{
            Long id=call.getArgument(0);lockOrder.add(id);
            var item=new PackagePriceQuote.Item(100L+id,"实际物料",BigDecimal.ONE,"袋",id,BigDecimal.TEN,BigDecimal.TEN,0,BigDecimal.TEN);
            return new PackagePriceQuote("q"+id,id,"套餐"+id,BigDecimal.ONE,BigDecimal.TEN,null,false,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,List.of(item));
        });
        List<OrderItemDTO> cart=new ArrayList<>();
        for(long id:new long[]{2,1}) {
            var line=new OrderItemDTO();line.setItemType(2);line.setPackageId(id);line.setQuantity(BigDecimal.ONE);line.setPackageQuoteId("q"+id);cart.add(line);
        }
        List<OrderItemDTO> result=ReflectionTestUtils.invokeMethod(service,"expandPackageSales",cart,1L,null,1L,new IdentityHashMap<>());
        assertEquals(List.of(1L,2L),lockOrder,"不同购物车顺序必须共用同一套餐主档锁序，不能A等B/B等A");
        assertEquals(List.of(2L,1L),result.stream().map(OrderItemDTO::getPackageId).toList(),"加锁顺序不能改写原购物车套餐组序");
    }
}
