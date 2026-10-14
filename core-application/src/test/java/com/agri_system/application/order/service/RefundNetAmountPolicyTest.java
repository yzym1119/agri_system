package com.agri_system.application.order.service;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class RefundNetAmountPolicyTest {
    @Test void ordinaryGoodsCannotRefundOrderDiscountAsCash() {
        Order o=new Order();o.setPayAmount(new BigDecimal("70.00"));
        OrderItem a=item(1,"60.00","3"),b=item(2,"40.00","2");
        var base=RefundAmountPolicy.refundableBaseAmounts(o,List.of(a,b));
        assertEquals(new BigDecimal("42.00"),base.get(1L));
        assertEquals(new BigDecimal("28.00"),base.get(2L));
    }
    @Test void oneOfThreeOrdinaryGoodsOnlyUsesOneThirdOfItsNetShare() {
        var a=item(1,"60.00","3");
        assertEquals(new BigDecimal("14.00"),RefundAmountPolicy.maximumForReturn(a,BigDecimal.ZERO,BigDecimal.ZERO,
                BigDecimal.ONE,new BigDecimal("42.00")));
    }
    @Test void returnQuantityCannotBeRoundedByAfterSalesStorage() {
        var a=item(1,"100.00","1");
        var error=assertThrows(IllegalArgumentException.class,()->RefundAmountPolicy.maximumForReturn(a,
                BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("0.00001"),new BigDecimal("100.00")));
        assertTrue(error.getMessage().contains("4位"));
    }
    @Test void halfABoxAndTrailingZerosAreValidPhysicalQuantities() {
        var a=item(1,"100.00","1");
        assertEquals(new BigDecimal("50.00"),RefundAmountPolicy.maximumForReturn(a,BigDecimal.ZERO,BigDecimal.ZERO,
                new BigDecimal("0.500000"),new BigDecimal("100.00")));
    }
    private OrderItem item(long id,String total,String qty) {
        OrderItem i=new OrderItem();i.setId(id);i.setTotalPrice(new BigDecimal(total));i.setQuantity(new BigDecimal(qty));return i;
    }
}
