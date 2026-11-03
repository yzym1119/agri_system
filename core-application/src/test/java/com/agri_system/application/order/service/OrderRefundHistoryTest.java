package com.agri_system.application.order.service;

import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRefundHistoryTest {
    private final AfterSalesRepository facts=mock(AfterSalesRepository.class);
    private final AfterSalesItemRepository items=mock(AfterSalesItemRepository.class);
    private AfterSales fact(long id,int status){var f=new AfterSales();f.setId(id);f.setStatus(status);return f;}
    private AfterSalesItem line(String q,String amount){var i=new AfterSalesItem();i.setOrderItemId(11L);i.setQuantity(q==null?null:new BigDecimal(q));i.setRefundAmount(amount==null?null:new BigDecimal(amount));return i;}
    @Test void 未拒绝占用与已完成累计保持同一口径() {
        when(facts.findByOrderId(7L)).thenReturn(List.of(fact(1,10),fact(2,50),fact(3,90)));
        when(items.findByAfterSalesId(1L)).thenReturn(List.of(line("0.5","2")));when(items.findByAfterSalesId(2L)).thenReturn(List.of(line("1","3")));
        var aggregate=OrderRefundHistory.load(7L,null,facts,items).get(11L);
        assertEquals(0,new BigDecimal("1.5").compareTo(aggregate.quantity));assertEquals(0,new BigDecimal("5").compareTo(aggregate.amount));verify(items,never()).findByAfterSalesId(3L);
    }
    @Test void 审批预览排除本售后不重复占用() {
        when(facts.findByOrderId(7L)).thenReturn(List.of(fact(1,10),fact(2,50)));when(items.findByAfterSalesId(2L)).thenReturn(List.of(line("1","3")));
        var aggregate=OrderRefundHistory.load(7L,1L,facts,items).get(11L);
        assertEquals(0,BigDecimal.ONE.compareTo(aggregate.quantity));assertEquals(0,new BigDecimal("3").compareTo(aggregate.amount));verify(items,never()).findByAfterSalesId(1L);
    }
    @Test void 空事实与缺数值沿原零值且不跨调用缓存() {
        when(facts.findByOrderId(7L)).thenReturn(List.of());assertTrue(OrderRefundHistory.load(7L,null,facts,items).isEmpty());
        when(facts.findByOrderId(7L)).thenReturn(List.of(fact(1,50)));when(items.findByAfterSalesId(1L)).thenReturn(List.of(line(null,null)));
        var aggregate=OrderRefundHistory.load(7L,null,facts,items).get(11L);assertEquals(BigDecimal.ZERO,aggregate.quantity);assertEquals(BigDecimal.ZERO,aggregate.amount);
    }
}
