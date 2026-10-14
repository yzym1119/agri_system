package com.agri_system.application.order.service;
import com.agri_system.domain.order.model.Order;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** 实际订单状态契约；POS净额与优惠另由真实应用服务测试覆盖。 */
class CreditOrderValidationTest {
    private Order order(int state,int method) {Order o=new Order();o.setOrderStatus(state);o.setPaymentMethod(method);return o;}
    @Test void paidCreditCanUseSameDirectReturnAsCash() {assertTrue(order(2,6).canRefund());}
    @Test void waitingDeliveryCreditCanBeReturned() {assertTrue(order(3,6).canRefund());}
    @Test void pickupCreditCanBeReturned() {assertTrue(order(4,6).canRefund());}
    @Test void unpaidOrderStillCannotReturnUnpaidGoods() {assertFalse(order(1,6).canRefund());}
    @Test void completedCreditUsesAfterSalesEntryLikeCash() {assertFalse(order(6,6).canRefund());}
}
