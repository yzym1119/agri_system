package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.order.model.*;
import com.agri_system.domain.order.repository.*;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际订单编排与实际支付服务，检查券、来源流水和持久化订单净额同时守恒。 */
class ClientCouponSettlementTest {
    private final OrderRepository orders=mock(OrderRepository.class);
    private final OrderItemRepository items=mock(OrderItemRepository.class);
    private final OrderPaymentRepository payments=mock(OrderPaymentRepository.class);
    private final OrderPaymentIntentRepository intents=mock(OrderPaymentIntentRepository.class);
    private final CustomerCouponRepository coupons=mock(CustomerCouponRepository.class);
    private final com.agri_system.domain.customer.repository.CustomerRepository customers=mock(com.agri_system.domain.customer.repository.CustomerRepository.class);
    private final WalletApplicationService wallet=mock(WalletApplicationService.class);
    private final com.agri_system.application.inventory.service.InventoryApplicationService inventory=mock(com.agri_system.application.inventory.service.InventoryApplicationService.class);
    private final List<OrderPayment> sources=new ArrayList<>();
    private final List<Order> savedOrders=new ArrayList<>();
    private final Map<String,OrderPaymentIntent> storedIntents=new HashMap<>();
    private OrderApplicationService service;
    private OrderPaymentService funding;
    private Order order;
    private CustomerCoupon coupon;

    @BeforeEach void fixture() {
        order=new Order();order.setId(1L);order.setCustomerId(10L);order.setStoreId(1L);order.setOrderType(2);order.setOrderStatus(1);
        order.setTotalAmount(m("100"));order.setDiscountAmount(m("0"));order.setCouponAmount(m("0"));order.setPayAmount(m("100"));
        when(orders.findById(1L)).thenAnswer(i->Optional.of(order));when(orders.findByIdForUpdate(1L)).thenAnswer(i->Optional.of(order));
        when(orders.save(any())).thenAnswer(i->{Order original=i.getArgument(0);Order snapshot=new Order();org.springframework.beans.BeanUtils.copyProperties(original,snapshot);savedOrders.add(snapshot);return original;});
        when(payments.save(any())).thenAnswer(i->{OrderPayment source=i.getArgument(0);source.setId(100L+sources.size());source.setRefundedAmount(m("0"));sources.add(source);return source;});
        when(payments.findByOrderId(1L)).thenAnswer(i->List.copyOf(sources));
        when(payments.findByOrderIdForUpdate(1L)).thenAnswer(i->List.copyOf(sources));
        when(payments.findByOrderIdAndOuterTransNo(eq(1L),anyString())).thenAnswer(i->sources.stream().filter(s->Objects.equals(s.getOuterTransNo(),i.getArgument(1))).findFirst());
        var customer=new Customer();customer.setId(10L);customer.setStatus(1);when(customers.findByIdWithLock(10L)).thenReturn(Optional.of(customer));
        var item=new OrderItem();item.setId(20L);item.setOrderId(1L);item.setProductId(30L);item.setQuantity(new BigDecimal("10"));item.setUnit("袋");item.setUnitPrice(m("10"));item.setTotalPrice(m("100"));item.setCostPriceSnapshot(m("1"));
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        var products=mock(com.agri_system.domain.product.repository.ProductRepository.class);var product=new Product();product.setId(30L);when(products.findById(30L)).thenReturn(Optional.of(product));
        coupon=new CustomerCoupon();coupon.setId(40L);coupon.setCustomerId(10L);coupon.setType(1);coupon.setAmount(m("30"));coupon.setStatus(1);coupon.setMinOrderAmount(m("0"));coupon.setStartTime(LocalDateTime.now().minusDays(1));coupon.setEndTime(LocalDateTime.now().plusDays(1));
        when(coupons.findById(40L)).thenReturn(Optional.of(coupon));when(coupons.findByIdForUpdate(40L)).thenReturn(Optional.of(coupon));
        when(wallet.getBalance(10L)).thenReturn(m("100"));when(wallet.getSpecialFundBalance(10L)).thenReturn(m("0"));
        var domain=mock(OrderDomainService.class);when(domain.calculateMaxSpecialFundAmount(anyList())).thenReturn(m("100"));
        var afterSales=mock(com.agri_system.domain.after_sales.repository.AfterSalesRepository.class);
        var afterItems=mock(com.agri_system.domain.after_sales.repository.AfterSalesItemRepository.class);
        funding=new OrderPaymentService(payments,coupons,wallet,domain,items,products,orders,customers,
                mock(com.agri_system.domain.credit.repository.CreditAccountRepository.class),mock(com.agri_system.domain.credit.repository.CreditRepaymentLogRepository.class),afterSales,afterItems);
        service=new OrderApplicationService(orders,items,payments,intents,mock(OrderDeliveryRepository.class),domain,funding,inventory,wallet,
                mock(com.agri_system.application.credit.service.CreditApplicationService.class),products,
                mock(com.agri_system.application.product.service.ProductPricingService.class),mock(com.agri_system.application.productpackage.service.PackagePricingService.class),
                mock(com.agri_system.domain.store.repository.StaffRepository.class),mock(com.agri_system.domain.store.repository.StoreRepository.class),customers,
                afterSales,afterItems,mock(org.springframework.context.ApplicationEventPublisher.class),mock(com.agri_system.domain.wallet.repository.WalletAuthCodeRepository.class),mock(PickupFulfillmentService.class),mock(ManualPriceAuditService.class));
        when(intents.save(any())).thenAnswer(i->{OrderPaymentIntent intent=i.getArgument(0);intent.setId(70L);storedIntents.put(intent.getIntentNo(),intent);return intent;});
        when(intents.findByIntentNo(anyString())).thenAnswer(i->Optional.ofNullable(storedIntents.get(i.getArgument(0))));
        ReflectionTestUtils.setField(service,"clientPaymentConfirmEnabled",true);
    }
    private BigDecimal m(String amount){return new BigDecimal(amount).setScale(2);}
    private OrderPayCommand command(boolean balance,boolean special){var command=new OrderPayCommand();command.setOrderId(1L);command.setCouponId(40L);command.setUseBalance(balance);command.setUseSpecialFund(special);return command;}
    private void assertNet(String net,String couponAmount,int method) {
        assertEquals(m(net),order.getPayAmount());assertEquals(m(couponAmount),order.getCouponAmount());assertEquals(method,order.getPaymentMethod());
        assertEquals(m(net),sources.stream().filter(p->p.getPaymentType()!=5).map(OrderPayment::getAmount).reduce(m("0"),BigDecimal::add));
        assertEquals(m(couponAmount),sources.stream().filter(p->p.getPaymentType()==5).map(OrderPayment::getAmount).reduce(m("0"),BigDecimal::add));
        assertTrue(savedOrders.stream().allMatch(o->o.getPayAmount().compareTo(m(net))==0&&o.getCouponAmount().compareTo(m(couponAmount))==0));
    }
    @Test void previewAndIntentDoNotConsumeCouponOrChangePendingNetAmount() {
        when(wallet.getBalance(10L)).thenReturn(m("20"));var request=command(true,false);request.setExternalPaymentType(3);
        var preview=service.previewOrderPayment(request,10L);assertEquals(m("50"),preview.getExternalAmount());assertEquals(m("30"),preview.getCouponAmount());
        var intent=service.createExternalPaymentIntent(request,10L);assertEquals(m("50"),intent.getExternalAmount());
        assertEquals(m("100"),order.getPayAmount());assertEquals(m("0"),order.getCouponAmount());assertEquals(1,coupon.getStatus());assertTrue(sources.isEmpty());assertTrue(savedOrders.isEmpty());
        verify(wallet,never()).deductBalance(any(),any(),any(),any(),any(),any());
    }
    @Test void couponAndWalletPaymentPersistNetBeforeStockAndRepeatReturnsOriginalSuccess() {
        var request=command(true,false);assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNet("70","30",4);assertEquals(3,order.getOrderStatus());
        assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNet("70","30",4);
        verify(wallet,times(1)).deductBalance(eq(10L),eq(m("70")),eq("ORDER"),eq(1L),eq(10L),anyString());verify(inventory,times(1)).outbound(any(),isNull());
        var sequence=inOrder(orders,customers,coupons,wallet,inventory);sequence.verify(orders).findByIdForUpdate(1L);sequence.verify(customers).findByIdWithLock(10L);sequence.verify(coupons).findByIdForUpdate(40L);sequence.verify(wallet).deductBalance(any(),any(),any(),any(),any(),any());sequence.verify(inventory).outbound(any(),isNull());
    }
    @Test void couponAndBothWalletSourcesPersistExactlyTheirNetSum() {
        when(wallet.getSpecialFundBalance(10L)).thenReturn(m("20"));assertNull(service.payOrder(command(true,true),10L));assertNet("70","30",4);
        verify(wallet).deductSpecialFund(eq(10L),eq(m("20")),eq("ORDER"),eq(1L),eq(10L),anyString());verify(wallet).deductBalance(eq(10L),eq(m("50")),eq("ORDER"),eq(1L),eq(10L),anyString());
    }
    @Test void zeroContributingSpecialSelectionCanReplayThePrincipalOnlySuccess() {
        var request=command(true,true);assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNet("70","30",4);
        verify(wallet,times(1)).deductBalance(any(),eq(m("70")),any(),any(),any(),any());verify(wallet,never()).deductSpecialFund(any(),any(),any(),any(),any(),any());
    }
    @Test void zeroContributingPrincipalSelectionCanReplayTheSpecialOnlySuccess() {
        when(wallet.getSpecialFundBalance(10L)).thenReturn(m("70"));var request=command(true,true);assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNet("70","30",4);
        verify(wallet,times(1)).deductSpecialFund(any(),eq(m("70")),any(),any(),any(),any());verify(wallet,never()).deductBalance(any(),any(),any(),any(),any(),any());
    }
    @Test void fullCouponPaymentPersistsZeroNetAndOnlyCouponMainMethod() {
        coupon.setAmount(m("100"));var request=command(false,false);assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));assertNet("0","100",5);assertNull(assertDoesNotThrow(()->service.payOrder(request,10L)));
        verify(wallet,never()).deductBalance(any(),any(),any(),any(),any(),any());verify(inventory,times(1)).outbound(any(),isNull());assertEquals(1,sources.size());
    }
    @Test void couponWalletAndConfirmedExternalAmountShareOneSavedNetAndCallbackReplay() {
        when(wallet.getBalance(10L)).thenReturn(m("20"));var request=command(true,false);request.setExternalPaymentType(3);
        var intent=service.createExternalPaymentIntent(request,10L);assertEquals(m("50"),intent.getExternalAmount());assertEquals(1,coupon.getStatus());
        var confirm=new OrderExternalPaymentConfirmCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("alipay-original-50");service.confirmExternalPayment(confirm,10L);
        assertNet("70","30",10);service.confirmExternalPayment(confirm,10L);assertNet("70","30",10);
        assertEquals(m("50"),sources.stream().filter(p->p.getPaymentType()==3).map(OrderPayment::getAmount).reduce(m("0"),BigDecimal::add));
        verify(wallet,times(1)).deductBalance(any(),eq(m("20")),any(),any(),any(),any());verify(inventory,times(1)).outbound(any(),isNull());
    }
    @Test void couponAndExternalOnlyCompletionUsesActualMainPaymentEnum() {
        var request=command(false,false);var intent=service.createExternalPaymentIntent(request,10L);assertEquals(m("70"),intent.getExternalAmount());
        var confirm=new OrderExternalPaymentCallbackCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("wechat-original-70");service.confirmExternalPaymentCallback(confirm);service.confirmExternalPaymentCallback(confirm);
        assertNet("70","30",2);verify(inventory,times(1)).outbound(any(),isNull());assertEquals(2,sources.size());
    }
    @Test void changedCouponRejectsOldExternalIntentBeforeAnyCouponWalletOrOrderWrite() {
        var request=command(false,false);var intent=service.createExternalPaymentIntent(request,10L);coupon.setAmount(m("40"));
        var confirm=new OrderExternalPaymentConfirmCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("old-wechat-70");
        assertThrows(IllegalArgumentException.class,()->service.confirmExternalPayment(confirm,10L));
        assertTrue(sources.isEmpty());assertTrue(savedOrders.isEmpty());assertEquals(1,coupon.getStatus());assertEquals(m("100"),order.getPayAmount());verifyNoInteractions(inventory);
    }
    @Test void stalePendingReadCannotChargeTheAlreadyCompletedCurrentOrder() {
        var stale=new Order();org.springframework.beans.BeanUtils.copyProperties(order,stale);order.setOrderStatus(3);order.setPayAmount(m("70"));order.setCouponAmount(m("30"));order.setPaymentMethod(4);
        var cp=new OrderPayment();cp.setOrderId(1L);cp.setPaymentType(5);cp.setAmount(m("30"));cp.setCouponId(40L);sources.add(cp);
        var bp=new OrderPayment();bp.setOrderId(1L);bp.setPaymentType(4);bp.setAmount(m("70"));sources.add(bp);
        coupon.setStatus(2);when(orders.findById(1L)).thenReturn(Optional.of(stale));
        assertNull(assertDoesNotThrow(()->service.payOrder(command(true,false),10L)));assertEquals(m("70"),order.getPayAmount());
        verify(coupons,never()).save(any());verify(payments,never()).save(any());verifyNoInteractions(inventory);
    }
}
