package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReturnTest {

    @InjectMocks
    private AfterSalesApplicationService afterSalesService;

    @Mock
    private AfterSalesRepository afterSalesRepository;
    @Mock
    private AfterSalesItemRepository afterSalesItemRepository;
    @Mock
    private AfterSalesDomainService afterSalesDomainService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryApplicationService inventoryApplicationService;
    @Mock
    private OrderPaymentService orderPaymentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Order order;
    private OrderItem orderItem;
    private Product product;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(100L);
        order.setStoreId(1L);
        order.setCustomerId(10L);
        order.setOrderStatus(6); // FINISHED
        order.setPayAmount(new BigDecimal("120.00"));
        order.setPaymentMethod(2);

        orderItem = new OrderItem();
        orderItem.setId(200L);
        orderItem.setOrderId(order.getId());
        orderItem.setProductId(300L);
        orderItem.setQuantity(new BigDecimal("2"));
        orderItem.setUnit("瓶");
        orderItem.setUnitPrice(new BigDecimal("60.00"));

        product = new Product();
        product.setId(300L);
        product.setAvgCostPrice(new BigDecimal("40.00"));
        product.setMainUnit("箱");
        product.setAuxUnit("瓶");
        product.setMainToAuxRatio(new BigDecimal("4"));

        when(orderRepository.findById(order.getId())).thenAnswer(inv -> Optional.of(order));
        when(orderRepository.findByIdForUpdate(order.getId())).thenAnswer(inv -> Optional.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(orderItem));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(afterSalesDomainService.generateAfterSalesNo()).thenReturn("AS202401010001");
        when(afterSalesRepository.findProcessingByOrderId(order.getId())).thenReturn(Optional.empty());
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of());
    }

    @Test
    void shouldRestockAndRefundForAdminReturn() {
        AfterSales stored = new AfterSales();
        stored.setId(999L);
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(invocation -> {
            AfterSales entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(999L);
            }
            stored.setId(entity.getId());
            stored.setStatus(entity.getStatus());
            stored.setAfterSalesNo(entity.getAfterSalesNo());
            stored.setRefundAmount(entity.getRefundAmount());
            stored.setOrderId(entity.getOrderId());
            return entity;
        });

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("120.00"));
        command.setReason("客户退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("2"));
        item.setRefundAmount(new BigDecimal("120.00"));
        command.setItems(List.of(item));

        Long afterSalesId = afterSalesService.createFromAdmin(command, 8L);

        assertEquals(999L, afterSalesId);
        verify(inventoryApplicationService, times(1)).inbound(argThat(inCommand ->
                inCommand.getQuantity().compareTo(new BigDecimal("2")) == 0
                        && "瓶".equals(inCommand.getUnit())
                        && Integer.valueOf(200).equals(inCommand.getSourceLineNo())
                        && Long.valueOf(999L).equals(inCommand.getSourceId())
                        && "SALES_RETURN".equals(inCommand.getSourceType())
                        && inCommand.getProductId().equals(product.getId())
        ), eq(8L));

        verify(orderPaymentService, times(1))
                .refund(eq(order), eq(new BigDecimal("120.00")), eq(8L));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(OrderRefundedEvent.class));
    }

    @Test
    void shouldAllowAdminReturnForPartRefundedOrder() {
        order.setOrderStatus(9);
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(invocation -> {
            AfterSales entity = invocation.getArgument(0);
            entity.setId(999L);
            return entity;
        });

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("60.00"));
        command.setReason("二次退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("1"));
        item.setRefundAmount(new BigDecimal("60.00"));
        command.setItems(List.of(item));

        Long afterSalesId = afterSalesService.createFromAdmin(command, 8L);

        assertEquals(999L, afterSalesId);
        verify(orderPaymentService).refund(eq(order), eq(new BigDecimal("60.00")), eq(8L));
        verify(eventPublisher).publishEvent(any(OrderRefundedEvent.class));
    }

    @Test
    void shouldMarkOrderRefundedWhenCumulativeRefundReachesPayAmount() {
        order.setOrderStatus(9);
        AfterSales finished = new AfterSales();
        finished.setId(501L);
        finished.setOrderId(order.getId());
        finished.setStatus(AfterSales.STATUS_FINISHED);
        finished.setRefundAmount(new BigDecimal("60.00"));
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of(finished));
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(invocation -> {
            AfterSales entity = invocation.getArgument(0);
            entity.setId(999L);
            return entity;
        });

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("60.00"));
        command.setReason("补退剩余金额");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("1"));
        item.setRefundAmount(new BigDecimal("60.00"));
        command.setItems(List.of(item));

        afterSalesService.createFromAdmin(command, 8L);

        verify(orderRepository).save(argThat(savedOrder ->
                savedOrder.getId().equals(order.getId()) && savedOrder.getOrderStatus() == 8
        ));
    }

    @Test
    void shouldRejectAdminReturnWhenCumulativeQuantityExceedsPurchased() {
        AfterSales finished = new AfterSales();
        finished.setId(501L);
        finished.setOrderId(order.getId());
        finished.setStatus(AfterSales.STATUS_FINISHED);
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of(finished));

        AfterSalesItem historyItem = new AfterSalesItem();
        historyItem.setAfterSalesId(501L);
        historyItem.setOrderItemId(orderItem.getId());
        historyItem.setProductId(product.getId());
        historyItem.setQuantity(new BigDecimal("1.5000"));
        historyItem.setRefundAmount(new BigDecimal("90.00"));
        when(afterSalesItemRepository.findByAfterSalesId(501L)).thenReturn(List.of(historyItem));

        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId());
        command.setRefundAmount(new BigDecimal("60.00"));
        command.setReason("超量退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId());
        item.setQuantity(new BigDecimal("1"));
        item.setRefundAmount(new BigDecimal("60.00"));
        command.setItems(List.of(item));

        assertThrows(IllegalArgumentException.class, () -> afterSalesService.createFromAdmin(command, 8L));
        verify(orderPaymentService, never()).refund(eq(order), eq(new BigDecimal("60.00")), eq(8L));
    }

    @Test
    void packagePartialReturnCannotRefundMoreThanItsAllocatedQuantityShare() {
        packageMaterial("3", "10.00");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> afterSalesService.createFromAdmin(returnCommand("1", "4.00"), 8L));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("金额"));
        verifyNoInteractions(orderPaymentService, inventoryApplicationService);
    }

    @Test
    void secondPackageReturnUsesCumulativeAmountAndCannotTakeAnotherMaterialsMoney() {
        packageMaterial("3", "10.00");
        history("1", "3.33");
        assertThrows(IllegalArgumentException.class,
                () -> afterSalesService.createFromAdmin(returnCommand("1", "3.35"), 8L));
        verifyNoInteractions(orderPaymentService, inventoryApplicationService);
    }

    @Test
    void lastPackageMaterialReturnRefundsTheRemainingCent() {
        packageMaterial("3", "10.00");
        history("2", "6.66");
        persistNewAfterSales();
        afterSalesService.createFromAdmin(returnCommand("1", "3.34"), 8L);
        verify(orderPaymentService).refund(eq(order), eq(new BigDecimal("3.34")), eq(8L));
    }

    @Test
    void duplicateMaterialInOneReturnRequestIsRejectedBeforeStockOrMoneyMoves() {
        packageMaterial("3", "10.00");
        AdminReturnCommand command = returnCommand("1", "5.00");
        command.setRefundAmount(new BigDecimal("10.00"));
        command.setItems(List.of(command.getItems().get(0), command.getItems().get(0)));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.createFromAdmin(command, 8L));
        verifyNoInteractions(orderPaymentService, inventoryApplicationService);
    }

    @Test
    void cumulativeRefundCannotExceedOriginalNetPayment() {
        history("0", "100.00");
        // 历史金额来自其它物料，当前行仍有60元额度，但订单实收只剩20元。
        when(afterSalesItemRepository.findByAfterSalesId(501L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> afterSalesService.createFromAdmin(returnCommand("1", "60.00"), 8L));
        verifyNoInteractions(orderPaymentService, inventoryApplicationService);
    }

    @Test
    void zeroAllocatedPackageMaterialCanReturnWithoutMoneyAndFinishesTheAfterSale() {
        packageMaterial("2", "0.00");
        orderItem.setUnitPrice(BigDecimal.ZERO);
        persistNewAfterSales();
        afterSalesService.createFromAdmin(returnCommand("1", "0.00"), 8L);
        verify(inventoryApplicationService).inbound(argThat(c -> c.getQuantity().compareTo(BigDecimal.ONE) == 0), eq(8L));
        verify(afterSalesRepository, atLeastOnce()).save(argThat(a -> a.getStatus() == AfterSales.STATUS_FINISHED));
        assertEquals(6, order.getOrderStatus());
        verifyNoInteractions(orderPaymentService);
    }

    @Test
    void zeroAllocatedGiftCanReturnAfterAllPaidMoneyWasAlreadyRefunded() {
        packageMaterial("2", "0.00");
        orderItem.setUnitPrice(BigDecimal.ZERO);
        order.setOrderStatus(8);
        history("0", "120.00");
        when(afterSalesItemRepository.findByAfterSalesId(501L)).thenReturn(List.of());
        persistNewAfterSales();
        afterSalesService.createFromAdmin(returnCommand("1", "0.00"), 8L);
        verify(inventoryApplicationService).inbound(any(), eq(8L));
        verifyNoInteractions(orderPaymentService);
        assertEquals(8, order.getOrderStatus());
    }

    @Test
    void ordinaryProductKeepsExistingAuthorizedManualRefundAmount() {
        orderItem.setTotalPrice(new BigDecimal("120.00"));
        persistNewAfterSales();
        afterSalesService.createFromAdmin(returnCommand("1", "100.00"), 8L);
        verify(orderPaymentService).refund(eq(order), eq(new BigDecimal("100.00")), eq(8L));
    }

    @Test
    void confirmedRefundStoresTheActuallyRefundedAmountForFutureCumulativeChecks() {
        AfterSales afterSales = new AfterSales();
        afterSales.setId(999L); afterSales.setOrderId(order.getId());
        afterSales.setStatus(AfterSales.STATUS_WAIT_REFUND);
        afterSales.setRefundAmount(new BigDecimal("60.00"));
        when(afterSalesRepository.findById(999L)).thenReturn(Optional.of(afterSales));
        when(afterSalesItemRepository.findByAfterSalesId(999L)).thenReturn(List.of());
        AfterSalesRefundCommand command = new AfterSalesRefundCommand();
        command.setAfterSalesId(999L); command.setRefundAmount(new BigDecimal("40.00"));
        afterSalesService.refund(command, 8L);
        assertEquals(new BigDecimal("40.00"), afterSales.getRefundAmount());
    }

    @Test
    void packageRefundCannotUseGrossMoneyFromOtherLinesAfterOrderDiscount() {
        discountedPackageMaterials();
        // 两个物料各成交10元，整单实收10元；每行净额5元，退1/3最多1.67元。
        assertThrows(IllegalArgumentException.class,
                () -> afterSalesService.createFromAdmin(returnCommand("1", "3.33"), 8L));
        verifyNoInteractions(orderPaymentService, inventoryApplicationService);
    }

    @Test
    void discountedPackageCanFinishAllQuantityReturnsAndRefundExactlyOriginalNetPayment() {
        OrderItem second = discountedPackageMaterials();
        java.util.ArrayList<AfterSales> saved = new java.util.ArrayList<>();
        java.util.Map<Long, List<AfterSalesItem>> details = new java.util.HashMap<>();
        when(afterSalesRepository.findByOrderId(order.getId())).thenAnswer(inv -> List.copyOf(saved));
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(inv -> {
            AfterSales entity = inv.getArgument(0);
            if (entity.getId() == null) { entity.setId(900L + saved.size()); saved.add(entity); }
            return entity;
        });
        doAnswer(inv -> {
            List<AfterSalesItem> rows = inv.getArgument(0);
            details.put(rows.get(0).getAfterSalesId(), List.copyOf(rows)); return null;
        }).when(afterSalesItemRepository).saveBatch(anyList());
        when(afterSalesItemRepository.findByAfterSalesId(anyLong()))
                .thenAnswer(inv -> details.getOrDefault(inv.getArgument(0), List.of()));

        afterSalesService.createFromAdmin(returnCommand("1", "1.67"), 8L);
        afterSalesService.createFromAdmin(returnCommand("1", "1.66"), 8L);
        afterSalesService.createFromAdmin(returnCommand("1", "1.67"), 8L);
        AdminReturnCommand last = returnCommand("3", "5.00");
        last.getItems().get(0).setOrderItemId(second.getId());
        afterSalesService.createFromAdmin(last, 8L);

        assertEquals(new BigDecimal("10.00"), saved.stream().map(AfterSales::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(8, order.getOrderStatus());
        assertEquals(new BigDecimal("6"), details.values().stream().flatMap(List::stream)
                .map(AfterSalesItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("10.00"), orderItem.getTotalPrice());
        assertEquals(new BigDecimal("10.00"), second.getTotalPrice());
    }

    @Test
    void packageMaterialWithPositiveGrossButZeroNetShareCanReturnWithoutFunds() {
        OrderItem second = discountedPackageMaterials();
        order.setPayAmount(new BigDecimal("0.01"));
        // 相同权重的一分钱按订单行ID稳定分配，第二行分到0元，原成交小计仍为10元。
        var bases = com.agri_system.application.order.service.RefundAmountPolicy.refundableBaseAmounts(
                order, List.of(second, orderItem));
        assertEquals(new BigDecimal("0.01"), bases.get(orderItem.getId()));
        assertEquals(new BigDecimal("0.00"), bases.get(second.getId()));
        persistNewAfterSales();
        AdminReturnCommand command = returnCommand("1", "0.00");
        command.getItems().get(0).setOrderItemId(second.getId());
        afterSalesService.createFromAdmin(command, 8L);
        verify(inventoryApplicationService).inbound(any(), eq(8L));
        verify(afterSalesRepository, atLeastOnce()).save(argThat(a -> a.getStatus() == AfterSales.STATUS_FINISHED));
        verifyNoInteractions(orderPaymentService);
        assertEquals(new BigDecimal("10.00"), second.getTotalPrice());
    }

    @Test
    void firstOfThreeMaterialsSharingOneCentCanReturnForZeroCash() {
        packageMaterial("3", "0.01"); order.setPayAmount(new BigDecimal("0.01"));
        persistNewAfterSales();
        // 1/3分钱按分舍入为0，首件必须能回库；剩余两件仍保留一分钱退款额度。
        afterSalesService.createFromAdmin(returnCommand("1", "0.00"), 8L);
        verify(inventoryApplicationService).inbound(argThat(c -> c.getQuantity().compareTo(BigDecimal.ONE) == 0), eq(8L));
        verify(afterSalesRepository, atLeastOnce()).save(argThat(a -> a.getStatus() == AfterSales.STATUS_FINISHED));
        verifyNoInteractions(orderPaymentService);
        assertEquals(6, order.getOrderStatus());
        assertEquals(new BigDecimal("0.01"), orderItem.getTotalPrice());
    }

    private OrderItem discountedPackageMaterials() {
        packageMaterial("3", "10.00"); order.setPayAmount(new BigDecimal("10.00"));
        OrderItem second = new OrderItem(); second.setId(201L); second.setOrderId(order.getId());
        second.setProductId(product.getId()); second.setPackageId(700L); second.setQuantity(new BigDecimal("3"));
        second.setUnit("瓶"); second.setUnitPrice(new BigDecimal("3.333333333333"));
        second.setTotalPrice(new BigDecimal("10.00"));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(orderItem, second));
        return second;
    }

    private void packageMaterial(String quantity, String totalPrice) {
        orderItem.setPackageId(700L);
        orderItem.setQuantity(new BigDecimal(quantity));
        orderItem.setTotalPrice(new BigDecimal(totalPrice));
        // 显示单价故意不能乘回分摊小计，金额校验必须以快照小计为准。
        orderItem.setUnitPrice(new BigDecimal("3.333333333333"));
    }

    private AdminReturnCommand returnCommand(String quantity, String amount) {
        AdminReturnCommand command = new AdminReturnCommand();
        command.setOrderId(order.getId()); command.setRefundAmount(new BigDecimal(amount));
        command.setReason("套餐物料退货");
        AdminReturnCommand.ReturnItem item = new AdminReturnCommand.ReturnItem();
        item.setOrderItemId(orderItem.getId()); item.setQuantity(new BigDecimal(quantity));
        item.setRefundAmount(new BigDecimal(amount)); command.setItems(List.of(item));
        return command;
    }

    private void history(String quantity, String amount) {
        AfterSales finished = new AfterSales();
        finished.setId(501L); finished.setOrderId(order.getId());
        finished.setStatus(AfterSales.STATUS_FINISHED); finished.setRefundAmount(new BigDecimal(amount));
        AfterSalesItem item = new AfterSalesItem();
        item.setAfterSalesId(501L); item.setOrderItemId(orderItem.getId());
        item.setProductId(product.getId()); item.setQuantity(new BigDecimal(quantity));
        item.setRefundAmount(new BigDecimal(amount));
        when(afterSalesRepository.findByOrderId(order.getId())).thenReturn(List.of(finished));
        when(afterSalesItemRepository.findByAfterSalesId(501L)).thenReturn(List.of(item));
    }

    private void persistNewAfterSales() {
        when(afterSalesRepository.save(any(AfterSales.class))).thenAnswer(inv -> {
            AfterSales entity = inv.getArgument(0);
            if (entity.getId() == null) entity.setId(999L);
            return entity;
        });
    }

    private AfterSalesItem buildAfterSalesItem() {
        AfterSalesItem item = new AfterSalesItem();
        item.setAfterSalesId(999L);
        item.setOrderItemId(orderItem.getId());
        item.setProductId(product.getId());
        item.setQuantity(new BigDecimal("2"));
        item.setRefundAmount(new BigDecimal("120.00"));
        return item;
    }
}
