package com.agri_system.application.catalog.service;

import com.agri_system.domain.catalog.query.PublicCatalogQuery;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.dto.vo.PriceQuote;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientCatalogServiceTest {
    final PublicCatalogQuery query=mock(PublicCatalogQuery.class);
    final ProductUnitService units=mock(ProductUnitService.class);
    final ProductPricingService prices=mock(ProductPricingService.class);
    final ClientCatalogService service=new ClientCatalogService(query,units,prices);
    PublicCatalogQuery.ProductView product(){return new PublicCatalogQuery.ProductView("7","公开商品","12瓶/箱","公开厂家","登记号",null,"档案说明",null,null);}
    ProductUnit unit(long id,String name,String ratio,boolean base){var u=new ProductUnit();u.setId(id);u.setProductId(7L);u.setUnitName(name);u.setStatus(1);u.setIsBaseUnit(base?1:0);u.setAllowSale(1);u.setBaseQuantityPerUnit(new BigDecimal(ratio));return u;}
    @Test void pageIsBoundedAndNoInternalFieldsOrUnsafeImagesAreReturned(){
        when(query.products(null,null)).thenReturn(java.util.stream.LongStream.rangeClosed(1,21).mapToObj(id->new PublicCatalogQuery.ProductSummary(""+id,"商品",null,null,"javascript:internal")).toList());
        var page=service.products(null,null);assertEquals(20,page.rows().size());assertEquals("20",page.nextAfter());assertNull(page.rows().get(0).imageUrl());
        assertEquals(List.of("productId","name","specification","manufacturer","imageUrl"),Arrays.stream(page.rows().get(0).getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }
    @Test void priceUsesOriginalOrdinaryQuoteAndNeverLeaksMemberFloorOrComputesFallback(){
        when(query.product(7L)).thenReturn(Optional.of(product()));when(units.getUnits(7L)).thenReturn(List.of(unit(1,"瓶","1",true),unit(2,"箱","12",false)));
        when(prices.quote(any())).thenAnswer(call->{var c=(com.agri_system.application.product.dto.vo.PriceContext)call.getArgument(0);assertEquals(1,c.customerType());assertNull(c.storeId());assertEquals("PUBLIC_CATALOG",c.channel());return new PriceQuote("private-quote",null,7L,null,c.channel(),1,null,"箱",2L,null,new BigDecimal("123.45"),"RETAIL",new BigDecimal("100"),false,false);});
        var detail=service.product(7L,"箱");assertEquals("瓶",detail.baseUnitName());assertFalse(detail.allowFractionalBaseQuantity());assertEquals("123.45",detail.referencePrice().unitPrice());assertEquals("12",detail.saleUnits().get(1).baseQuantityPerUnit());
    }
    @Test void missingUnitsOrQuoteNeverBecomeZeroOrFakeSalesCapability(){
        when(query.product(7L)).thenReturn(Optional.of(product()));when(units.getUnits(7L)).thenThrow(new IllegalStateException("未配置"));
        var detail=service.product(7L,null);assertNull(detail.baseUnitName());assertTrue(detail.saleUnits().isEmpty());assertNull(detail.referencePrice().unitPrice());assertEquals("CONFIRM_WITH_STORE",detail.referencePrice().state());
    }
    @Test void withdrawalDuringReadDoesNotReturnOldDetailOrAQuoteForAnotherProduct(){
        when(query.product(7L)).thenReturn(Optional.of(product()),Optional.empty());when(units.getUnits(7L)).thenReturn(List.of(unit(1,"瓶","1",true)));
        assertThrows(IllegalArgumentException.class,()->service.product(7L,null));
    }
    @Test void invalidCursorsAndKeywordsAreRejectedBeforeQuery(){
        assertThrows(IllegalArgumentException.class,()->service.products(-1L,null));assertThrows(IllegalArgumentException.class,()->service.products(null,"x".repeat(101)));assertThrows(IllegalArgumentException.class,()->service.products(null,"x\n"));assertThrows(IllegalArgumentException.class,()->service.product(0L,null));verifyNoInteractions(query);
    }
    @Test void quoteForAnotherSelectedUnitCannotBeShownAsTheRequestedUnit(){
        when(query.product(7L)).thenReturn(Optional.of(product()));when(units.getUnits(7L)).thenReturn(List.of(unit(1,"瓶","1",true),unit(2,"箱","12",false)));
        when(prices.quote(any())).thenReturn(new PriceQuote("q",null,7L,null,"PUBLIC_CATALOG",1,null,"瓶",1L,null,new BigDecimal("10.00"),"RETAIL",null,false,false));
        assertEquals("CONFIRM_WITH_STORE",service.product(7L,"箱").referencePrice().state());
    }
}
