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
    private final OrderDeliveryRepository deliveries=mock(OrderDeliveryRepository.class);
    private final com.agri_system.domain.store.repository.StaffRepository staff=mock(com.agri_system.domain.store.repository.StaffRepository.class);
    private final CustomerCouponRepository coupons=mock(CustomerCouponRepository.class);
    private final com.agri_system.domain.customer.repository.CustomerRepository customers=mock(com.agri_system.domain.customer.repository.CustomerRepository.class);
    private final WalletApplicationService wallet=mock(WalletApplicationService.class);
    private final com.agri_system.application.inventory.service.InventoryApplicationService inventory=mock(com.agri_system.application.inventory.service.InventoryApplicationService.class);
    private final org.springframework.transaction.PlatformTransactionManager tx=mock(org.springframework.transaction.PlatformTransactionManager.class);
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
        service=new OrderApplicationService(
                null,
                null,
                null,
                new com.agri_system.application.order.service.ClientOrderPaymentService(orders, items, payments, intents, deliveries, funding, inventory, customers,
                        tx),
                new com.agri_system.application.order.service.OrderFulfillmentService(orders, deliveries, staff, afterSales, mock(org.springframework.context.ApplicationEventPublisher.class), org.mockito.Mockito.mock(com.agri_system.domain.system.log.repository.SysOperationLogRepository.class)),
                null);
        when(intents.save(any())).thenAnswer(i->{OrderPaymentIntent intent=i.getArgument(0);intent.setId(70L);storedIntents.put(intent.getIntentNo(),intent);return intent;});
        when(intents.findByIntentNo(anyString())).thenAnswer(i->Optional.ofNullable(storedIntents.get(i.getArgument(0))));
        lenient().when(intents.findByRequestKey(anyString())).thenAnswer(i->storedIntents.values().stream()
                .filter(x->i.getArgument(0).equals(x.getRequestKey())).findFirst());
        // 确认前条件锁定：单测环境默认意图均为 PENDING 可锁定
        lenient().when(intents.claimPendingByIntentNo(anyString())).thenReturn(1);
        lenient().when(intents.expirePendingByIntentNo(anyString())).thenReturn(1);
        ReflectionTestUtils.setField(ReflectionTestUtils.getField(service,"clientOrderPaymentService"),"clientPaymentConfirmEnabled",true);
    }
    private BigDecimal m(String amount){return new BigDecimal(amount).setScale(2);}
    private OrderPayCommand command(boolean balance,boolean special){var command=new OrderPayCommand();command.setOrderId(1L);command.setCouponId(40L);command.setUseBalance(balance);command.setUseSpecialFund(special);command.setRequestKey("rk-"+java.util.UUID.randomUUID());return command;}
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
        var confirm=new OrderExternalPaymentConfirmCommand();confirm.setOrderId(order.getId());confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("alipay-original-50");confirm.setExternalPaymentType(3);service.confirmExternalPayment(confirm,10L);
        assertNet("70","30",10);service.confirmExternalPayment(confirm,10L);assertNet("70","30",10);
        assertEquals(m("50"),sources.stream().filter(p->p.getPaymentType()==3).map(OrderPayment::getAmount).reduce(m("0"),BigDecimal::add));
        verify(wallet,times(1)).deductBalance(any(),eq(m("20")),any(),any(),any(),any());verify(inventory,times(1)).outbound(any(),isNull());
    }
    @Test void couponAndExternalOnlyCompletionUsesActualMainPaymentEnum() {
        var request=command(false,false);var intent=service.createExternalPaymentIntent(request,10L);assertEquals(m("70"),intent.getExternalAmount());
        var confirm=new OrderExternalPaymentCallbackCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("wechat-original-70");service.confirmExternalPaymentCallback(confirm);service.confirmExternalPaymentCallback(confirm);
        assertNet("70","30",2);verify(inventory,times(1)).outbound(any(),isNull());assertEquals(2,sources.size());
    }
    @Test void duplicateRequestKeyReplaysSameIntentWithoutExpiringOrCreating() {
        var first=command(false,false);first.setRequestKey("rk-pay-1");
        var created=service.createExternalPaymentIntent(first,10L);
        var retry=command(false,false);retry.setRequestKey("rk-pay-1");
        var replayed=service.createExternalPaymentIntent(retry,10L);
        assertEquals(created.getIntentNo(),replayed.getIntentNo());
        assertEquals(created.getExternalAmount(),replayed.getExternalAmount());
        // 重试不得作废已创建意图，也不得新建
        verify(intents,times(1)).expirePendingByOrderId(1L);
        verify(intents,times(1)).save(any());
    }
    @Test void replayKeepsFirstSnapshotWhenWalletOrCouponChanges() {
        // 充值、消费或券面额变化都不改变已发起的资金拆分：同键重试必须拿到第一次的结果
        when(wallet.getBalance(10L)).thenReturn(m("20"));
        var first=command(true,false);first.setRequestKey("rk-snapshot");
        var created=service.createExternalPaymentIntent(first,10L);
        assertEquals(m("50"),created.getExternalAmount());
        when(wallet.getBalance(10L)).thenReturn(m("60"));
        coupon.setAmount(m("40"));
        var retry=command(true,false);retry.setRequestKey("rk-snapshot");
        var replayed=service.createExternalPaymentIntent(retry,10L);
        assertEquals(m("50"),replayed.getExternalAmount());
        assertEquals(m("20"),replayed.getPreview().getBalanceAmount());
        assertEquals(m("30"),replayed.getPreview().getCouponAmount());
        verify(intents,times(1)).save(any());
    }
    @Test void replayWithSameAmountButDifferentWalletOptionIsRejected() {
        // 余额为 0 时选不选余额，外部金额都是 70：金额相等不能证明选项相同
        when(wallet.getBalance(10L)).thenReturn(m("0"));
        var first=command(true,false);first.setRequestKey("rk-option");
        assertEquals(m("70"),service.createExternalPaymentIntent(first,10L).getExternalAmount());
        var conflict=command(false,false);conflict.setRequestKey("rk-option");
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.createExternalPaymentIntent(conflict,10L));
        assertTrue(failure.getMessage().contains("选项"),failure.getMessage());
        verify(intents,times(1)).expirePendingByOrderId(1L);verify(intents,times(1)).save(any());
    }
    @Test void replayWithDifferentExternalChannelIsRejected() {
        var first=command(false,false);first.setRequestKey("rk-channel");first.setExternalPaymentType(2);
        service.createExternalPaymentIntent(first,10L);
        var conflict=command(false,false);conflict.setRequestKey("rk-channel");conflict.setExternalPaymentType(3);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.createExternalPaymentIntent(conflict,10L));
        assertTrue(failure.getMessage().contains("选项"),failure.getMessage());
    }
    @Test void replayOfInvalidatedIntentIsRejectedWithoutReturningOldIntent() {
        var request=command(false,false);request.setRequestKey("rk-invalid");
        var intent=service.createExternalPaymentIntent(request,10L);
        storedIntents.get(intent.getIntentNo()).setStatus(3); // 已被新意图替代或已标记失效
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.createExternalPaymentIntent(request,10L));
        assertTrue(failure.getMessage().contains("已失效"),failure.getMessage());
    }
    @Test void replayOfExpiredIntentIsRejectedAndMarkedExpired() {
        when(tx.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        var request=command(false,false);request.setRequestKey("rk-expired");
        var intent=service.createExternalPaymentIntent(request,10L);
        storedIntents.get(intent.getIntentNo()).setExpireAt(LocalDateTime.now().minusMinutes(1));
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.createExternalPaymentIntent(request,10L));
        assertTrue(failure.getMessage().contains("已过期"),failure.getMessage());
        verify(intents).expirePendingByIntentNo(intent.getIntentNo());
    }
    @Test void intentCreationWithoutRequestKeyIsRejected() {
        var request=command(false,false);request.setRequestKey(null);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.createExternalPaymentIntent(request,10L));
        assertTrue(failure.getMessage().contains("requestKey"),failure.getMessage());
        assertTrue(storedIntents.isEmpty());verifyNoInteractions(inventory);
    }
    @Test void alipayRequestPreviewsAlipayChannel() {
        var request=command(false,false);request.setExternalPaymentType(3);
        var payments=service.previewOrderPayment(request,10L).getPayments();
        assertEquals(3,payments.get(payments.size()-1).getPaymentType());
    }
    @Test void confirmDeclaredChannelMustMatchIntentChannel() {
        var request=command(false,false);request.setExternalPaymentType(2); // 微信发起
        var intent=service.createExternalPaymentIntent(request,10L);
        var confirm=new OrderExternalPaymentConfirmCommand();confirm.setOrderId(order.getId());confirm.setIntentNo(intent.getIntentNo());
        confirm.setExternalTransactionNo("ali-901");confirm.setExternalPaymentType(3); // 支付宝流水确认微信意图
        assertThrows(IllegalArgumentException.class,()->service.confirmExternalPayment(confirm,10L));
        assertTrue(sources.isEmpty());assertTrue(savedOrders.isEmpty());
    }
    @Test void changedCouponRejectsOldExternalIntentBeforeAnyCouponWalletOrOrderWrite() {
        var request=command(false,false);var intent=service.createExternalPaymentIntent(request,10L);coupon.setAmount(m("40"));
        var confirm=new OrderExternalPaymentConfirmCommand();confirm.setOrderId(order.getId());confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("old-wechat-70");
        assertThrows(IllegalArgumentException.class,()->service.confirmExternalPayment(confirm,10L));
        assertTrue(sources.isEmpty());assertTrue(savedOrders.isEmpty());assertEquals(1,coupon.getStatus());assertEquals(m("100"),order.getPayAmount());verifyNoInteractions(inventory);
    }
    @Test void supersededIntentCannotBeConfirmedAndNoMoneyMoves() {
        // 意图A被新意图B替代（expirePendingByOrderId 置 status=3）后，A的回调到达：
        // 必须条件锁定失败→重查确认已失效→拒绝，绝不进入正常结算造成双重收款。
        var request=command(false,false);var intent=service.createExternalPaymentIntent(request,10L);
        var confirm=new OrderExternalPaymentCallbackCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("wechat-stale-70");
        var stale=storedIntents.get(intent.getIntentNo());stale.setStatus(3); // 模拟已被替代失效
        when(intents.claimPendingByIntentNo(intent.getIntentNo())).thenReturn(0); // 条件锁定失败
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.confirmExternalPaymentCallback(confirm));
        assertTrue(failure.getMessage().contains("已失效"),"应显式拒绝已失效意图: "+failure.getMessage());
        assertTrue(failure.getMessage().contains("wechat-stale-70"),"错误信息应带流水号供对账");
        assertTrue(sources.isEmpty());assertTrue(savedOrders.isEmpty());
        assertEquals(1,order.getOrderStatus());assertEquals(1,coupon.getStatus());
        verifyNoInteractions(inventory);
    }
    @Test void repeatedCallbackWithSameTransactionReplaysByLatestCompletedStatus() {
        // 重复回调按「重查最新状态」分流：已是 COMPLETED(2) 且同一流水 → 安全重放，不得二次收款
        var request=command(false,false);var intent=service.createExternalPaymentIntent(request,10L);
        var confirm=new OrderExternalPaymentCallbackCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("wechat-x-70");
        service.confirmExternalPaymentCallback(confirm);assertNet("70","30",2);
        when(intents.claimPendingByIntentNo(intent.getIntentNo())).thenReturn(0);
        assertDoesNotThrow(()->service.confirmExternalPaymentCallback(confirm));
        assertEquals(m("70"),sources.stream().filter(p->p.getPaymentType()!=5).map(OrderPayment::getAmount).reduce(m("0"),BigDecimal::add),"重放不得二次收款");
    }
    @Test void expiredAndInvalidatedCallbackGoesToReconciliationInsteadOfRawFailure() {
        // 先被替代失效(3)、后到期：必须按最新状态走对账告警，不能先做过期条件更新而更新 0 行抛底层异常
        var request=command(false,false);request.setRequestKey("rk-stale-expired");
        var intent=service.createExternalPaymentIntent(request,10L);
        var stale=storedIntents.get(intent.getIntentNo());stale.setStatus(3);stale.setExpireAt(LocalDateTime.now().minusMinutes(5));
        var confirm=new OrderExternalPaymentCallbackCommand();confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("wechat-paid-but-invalid");
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->service.confirmExternalPaymentCallback(confirm));
        assertTrue(failure.getMessage().contains("对账"),failure.getMessage());
        assertTrue(failure.getMessage().contains("wechat-paid-but-invalid"),"错误信息应带流水号供对账");
        verify(intents,never()).expirePendingByIntentNo(anyString());
        assertTrue(sources.isEmpty());assertTrue(savedOrders.isEmpty());
    }
    @Test void internalPaymentVoidsPendingExternalIntentBeforeSettling() {
        // 客户已发起外部支付又改用钱包结清：必须先作废待确认意图，避免同一订单并行两个支付尝试
        var external=command(false,false);external.setRequestKey("rk-pending");
        service.createExternalPaymentIntent(external,10L);
        when(intents.expirePendingByOrderId(1L)).thenReturn(1);
        assertNull(service.payOrder(command(true,false),10L));
        assertEquals(3,order.getOrderStatus());
        verify(intents,times(2)).expirePendingByOrderId(1L);
    }
    @Test void stalePendingReadCannotChargeTheAlreadyCompletedCurrentOrder() {
        var stale=new Order();org.springframework.beans.BeanUtils.copyProperties(order,stale);order.setOrderStatus(3);order.setPayAmount(m("70"));order.setCouponAmount(m("30"));order.setPaymentMethod(4);
        var cp=new OrderPayment();cp.setOrderId(1L);cp.setPaymentType(5);cp.setAmount(m("30"));cp.setCouponId(40L);sources.add(cp);
        var bp=new OrderPayment();bp.setOrderId(1L);bp.setPaymentType(4);bp.setAmount(m("70"));sources.add(bp);
        coupon.setStatus(2);when(orders.findById(1L)).thenReturn(Optional.of(stale));
        assertNull(assertDoesNotThrow(()->service.payOrder(command(true,false),10L)));assertEquals(m("70"),order.getPayAmount());
        verify(coupons,never()).save(any());verify(payments,never()).save(any());verifyNoInteractions(inventory);
    }

    @Test void localDeliveryWalletPaymentBecomesDispatchableWithoutPaymentReplayRewindingIt() {
        assertDeliveryPaymentChain(2);
    }
    @Test void expressDeliveryWalletPaymentBecomesDispatchableWithoutPaymentReplayRewindingIt() {
        assertDeliveryPaymentChain(3);
    }
    @Test void deliveryIntentDoesNotDispatchUntilActualExternalConfirmation() {
        OrderDelivery delivery=onlineDelivery(2);
        when(wallet.getBalance(10L)).thenReturn(m("20"));var request=command(true,false);request.setExternalPaymentType(3);
        service.previewOrderPayment(request,10L);var intent=service.createExternalPaymentIntent(request,10L);
        assertEquals(1,delivery.getDeliveryStatus());verify(deliveries,never()).save(any());
        var confirm=new OrderExternalPaymentConfirmCommand();confirm.setOrderId(order.getId());confirm.setIntentNo(intent.getIntentNo());confirm.setExternalTransactionNo("delivery-alipay-50");confirm.setExternalPaymentType(3);
        service.confirmExternalPayment(confirm,10L);assertEquals(2,delivery.getDeliveryStatus());
        service.shipOrder(shipment(2),8L);service.confirmExternalPayment(confirm,10L);
        assertEquals(3,delivery.getDeliveryStatus());verify(inventory,times(1)).outbound(any(),isNull());
    }
    private void assertDeliveryPaymentChain(int type) {
        OrderDelivery delivery=onlineDelivery(type);var request=command(true,false);
        service.previewOrderPayment(request,10L);assertEquals(1,delivery.getDeliveryStatus());
        service.payOrder(request,10L);assertEquals(4,order.getOrderStatus());assertEquals(2,delivery.getDeliveryStatus());
        assertNet("70","30",4);
        service.shipOrder(shipment(type),8L);var dispatched=delivery.getDeliveryAt();
        service.payOrder(request,10L);assertEquals(3,delivery.getDeliveryStatus());assertEquals(dispatched,delivery.getDeliveryAt());
        service.confirmReceipt(1L,8L);var received=delivery.getReceivedAt();
        service.payOrder(request,10L);assertEquals(4,delivery.getDeliveryStatus());assertEquals(received,delivery.getReceivedAt());
        verify(inventory,times(1)).outbound(any(),isNull());assertEquals(2,sources.size());
    }
    private OrderDelivery onlineDelivery(int type) {
        order.setOrderType(3);var delivery=new OrderDelivery();delivery.setOrderId(1L);delivery.setDeliveryType(type);delivery.setDeliveryStatus(1);
        when(deliveries.findByOrderId(1L)).thenReturn(Optional.of(delivery));when(orders.hasRemainingDeliveryQuantity(1L)).thenReturn(true);
        var driver=new com.agri_system.domain.store.model.Staff();driver.setId(8L);driver.setStoreId(1L);driver.setStatus(1);
        when(staff.findById(8L)).thenReturn(Optional.of(driver));return delivery;
    }
    private OrderShipCommand shipment(int type) {
        var command=new OrderShipCommand();command.setOrderId(1L);
        if(type==2) command.setDeliveryStaffId(8L);else {command.setLogisticsCompany("物流");command.setTrackingNo("delivery-tracking");}
        return command;
    }
}
