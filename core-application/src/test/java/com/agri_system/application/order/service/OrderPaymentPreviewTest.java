package com.agri_system.application.order.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实预览算法和商品专用金上限；仅隔离读取依赖，不连接固定库或执行支付。 */
class OrderPaymentPreviewTest {
    @Test
    void coupon20SpecialFund30AndExternal50ArePreviewedWithoutAnyWrites() {
        var payments = mock(OrderPaymentRepository.class);
        var coupons = mock(CustomerCouponRepository.class);
        var wallet = mock(WalletApplicationService.class);
        var items = mock(OrderItemRepository.class);
        var products = mock(ProductRepository.class);
        var orders = mock(OrderRepository.class);
        var customers = mock(CustomerRepository.class);
        var credit = mock(CreditAccountRepository.class);
        var repayments = mock(CreditRepaymentLogRepository.class);
        var afterSales = mock(AfterSalesRepository.class);
        var afterSalesItems = mock(AfterSalesItemRepository.class);
        var service = new OrderPaymentService(payments, coupons, wallet, new OrderDomainService(), items, products,
                orders, customers, credit, repayments, afterSales, afterSalesItems);

        Order order = new Order();
        order.setId(101L);
        order.setCustomerId(202L);
        order.setPayAmount(new BigDecimal("100.00"));
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setId(303L);
        coupon.setCustomerId(202L);
        coupon.setType(1);
        coupon.setStatus(1);
        coupon.setAmount(new BigDecimal("20.00"));
        coupon.setMinOrderAmount(new BigDecimal("100.00"));
        // 无时间限制，避免依赖当前时间；保留真实客户归属及门槛校验。
        OrderItem eligible = new OrderItem();
        eligible.setProductId(401L);
        eligible.setTotalPrice(new BigDecimal("60.00"));
        eligible.setAllowSpecialFund(1);
        OrderItem ineligible = new OrderItem();
        ineligible.setProductId(402L);
        ineligible.setTotalPrice(new BigDecimal("40.00"));
        ineligible.setAllowSpecialFund(0);
        when(coupons.findById(303L)).thenReturn(Optional.of(coupon));
        when(items.findByOrderId(101L)).thenReturn(List.of(eligible, ineligible));
        when(products.findById(401L)).thenReturn(Optional.empty());
        when(products.findById(402L)).thenReturn(Optional.empty());
        when(wallet.getSpecialFundBalance(202L)).thenReturn(new BigDecimal("30.00"));
        when(wallet.getBalance(202L)).thenReturn(BigDecimal.ZERO);

        var preview = service.previewPayment(order, true, true, 303L);

        assertMoney("100.00", preview.getTotalAmount());
        assertMoney("20.00", preview.getCouponAmount());
        assertMoney("30.00", preview.getSpecialFundAmount());
        assertMoney("0.00", preview.getBalanceAmount());
        assertMoney("50.00", preview.getExternalAmount());
        assertEquals(List.of(5, 8, 2), preview.getPayments().stream().map(p -> p.getPaymentType()).toList());
        assertEquals(List.of(new BigDecimal("20.00"), new BigDecimal("30.00"), new BigDecimal("50.00")),
                preview.getPayments().stream().map(p -> p.getAmount()).toList());
        assertEquals(303L, preview.getPayments().get(0).getCouponId());
        assertMoney("100.00", preview.getPayments().stream().map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(1, coupon.getStatus());
        assertNull(coupon.getUsedOrderId());
        assertNull(coupon.getUsedTime());
        assertMoney("100.00", order.getPayAmount());

        // 穷举允许的读取调用，剩余任何保存、扣款或核销调用都会使测试失败。
        verify(coupons).findById(303L);
        verify(items, times(2)).findByOrderId(101L);
        verify(products).findById(401L);
        verify(products).findById(402L);
        verify(wallet).getSpecialFundBalance(202L);
        verify(wallet).getBalance(202L);
        verifyNoMoreInteractions(coupons, items, products, wallet);
        verifyNoInteractions(payments, orders, customers, credit, repayments, afterSales, afterSalesItems);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
