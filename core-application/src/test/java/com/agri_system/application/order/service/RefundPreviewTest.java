package com.agri_system.application.order.service;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.domain.order.model.*;
import com.agri_system.domain.credit.model.*;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.after_sales.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** 实际订单预览与资金服务，存储边界为有状态替身。 */
class RefundPreviewTest {
    private final com.agri_system.domain.order.repository.OrderRepository orders=mock(com.agri_system.domain.order.repository.OrderRepository.class);
    private final com.agri_system.domain.order.repository.OrderItemRepository items=mock(com.agri_system.domain.order.repository.OrderItemRepository.class);
    private final com.agri_system.domain.order.repository.OrderPaymentRepository payments=mock(com.agri_system.domain.order.repository.OrderPaymentRepository.class);
    private final com.agri_system.domain.customer.repository.CustomerRepository customers=mock(com.agri_system.domain.customer.repository.CustomerRepository.class);
    private final com.agri_system.domain.credit.repository.CreditAccountRepository accounts=mock(com.agri_system.domain.credit.repository.CreditAccountRepository.class);
    private final com.agri_system.domain.credit.repository.CreditRepaymentLogRepository repayments=mock(com.agri_system.domain.credit.repository.CreditRepaymentLogRepository.class);
    private final com.agri_system.domain.after_sales.repository.AfterSalesRepository afterSales=mock(com.agri_system.domain.after_sales.repository.AfterSalesRepository.class);
    private final com.agri_system.domain.after_sales.repository.AfterSalesItemRepository afterItems=mock(com.agri_system.domain.after_sales.repository.AfterSalesItemRepository.class);
    private final com.agri_system.application.inventory.service.InventoryApplicationService inventory=mock(com.agri_system.application.inventory.service.InventoryApplicationService.class);
    private final List<CreditRepaymentLog> repaymentSources=new ArrayList<>();
    private OrderApplicationService service;
    private Order order;
    private CreditAccount account;
    @BeforeEach void fixture() {
        order=new Order();order.setId(1L);order.setCustomerId(10L);order.setStoreId(1L);order.setOrderStatus(4);order.setPaymentMethod(6);order.setPayAmount(m("100"));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        account=new CreditAccount();account.setId(11L);account.setOrderId(1L);account.setCustomerId(10L);account.setStoreId(1L);
        account.setCreditAmount(m("100"));account.setPaidAmount(m("0"));account.setRemainingAmount(m("100"));
        when(accounts.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(account));
        var customer=new Customer();customer.setId(10L);when(customers.findByIdWithLock(10L)).thenReturn(Optional.of(customer));
        var payment=new OrderPayment();payment.setId(100L);payment.setOrderId(1L);payment.setPaymentType(6);payment.setAmount(m("100"));payment.setRefundedAmount(m("0"));
        when(payments.findByOrderIdForUpdate(1L)).thenReturn(List.of(payment));
        when(repayments.findByCreditAccountIdForUpdate(11L)).thenAnswer(i->repaymentSources);
        var item=new OrderItem();item.setId(1L);item.setOrderId(1L);item.setProductId(20L);item.setTotalPrice(m("100"));item.setQuantity(new BigDecimal("10"));item.setUnit("袋");
        when(items.findByOrderId(1L)).thenReturn(List.of(item));
        var wallet=mock(com.agri_system.application.wallet.service.WalletApplicationService.class);
        var productRepo=mock(com.agri_system.domain.product.repository.ProductRepository.class);
        var domain=mock(com.agri_system.domain.order.service.OrderDomainService.class);
        var funding=new OrderPaymentService(payments,mock(com.agri_system.domain.coupon.repository.CustomerCouponRepository.class),wallet,domain,items,productRepo,orders,customers,accounts,repayments,afterSales,afterItems);
        service=new OrderApplicationService(
                null,
                null,
                null,
                null,
                null,
                new com.agri_system.application.order.service.OrderRefundService(orders, items, funding, inventory, productRepo, afterSales, afterItems, mock(org.springframework.context.ApplicationEventPublisher.class)));
    }
    private BigDecimal m(String s){return new BigDecimal(s).setScale(2);}
    private OrderRefundPreviewCommand request(String quantity){var c=new OrderRefundPreviewCommand();var i=new OrderRefundPreviewCommand.Item();i.setOrderItemId(1L);i.setQuantity(new BigDecimal(quantity));c.setItems(List.of(i));return c;}
    private com.agri_system.application.after_sales.service.AfterSalesApplicationService afterSalesService() {
        var products=mock(com.agri_system.domain.product.repository.ProductRepository.class);var product=new com.agri_system.domain.product.model.Product();product.setId(20L);product.setAvgCostPrice(m("5"));when(products.findById(20L)).thenReturn(Optional.of(product));
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        return new com.agri_system.application.after_sales.service.AfterSalesApplicationService(afterSales,afterItems,
                mock(com.agri_system.domain.after_sales.service.AfterSalesDomainService.class),orders,items,products,customers,
                mock(com.agri_system.domain.store.repository.StoreRepository.class),mock(com.agri_system.domain.store.repository.StaffRepository.class),
                inventory,(OrderPaymentService)org.springframework.test.util.ReflectionTestUtils.getField(org.springframework.test.util.ReflectionTestUtils.getField(service,"refundService"),"paymentService"),service,
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }
    private AfterSales pendingCancellation(String total,String childAmount) {
        var applied=new AfterSales();applied.setId(50L);applied.setOrderId(1L);applied.setCustomerId(10L);applied.setStoreId(1L);applied.setStatus(AfterSales.STATUS_APPLIED);applied.setType(AfterSales.TYPE_CLIENT_CANCEL);applied.setRefundAmount(m(total));
        when(afterSales.findById(50L)).thenReturn(Optional.of(applied));when(afterSales.findByIdForUpdate(50L)).thenReturn(Optional.of(applied));
        when(afterSales.findProcessingByOrderId(1L)).thenAnswer(i->applied.isPending()||applied.isWaitRefund()?Optional.of(applied):Optional.empty());
        when(afterSales.findByOrderId(1L)).thenReturn(List.of(applied));
        var child=new AfterSalesItem();child.setId(60L);child.setAfterSalesId(50L);child.setOrderItemId(1L);child.setProductId(20L);child.setQuantity(new BigDecimal("10"));child.setRefundAmount(m(childAmount));when(afterItems.findByAfterSalesId(50L)).thenReturn(List.of(child));
        return applied;
    }
    private void cashSources(String net,BigDecimal counter) {
        order.setPaymentMethod(1);order.setPayAmount(m(net));when(accounts.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        var p=new OrderPayment();p.setId(100L);p.setOrderId(1L);p.setPaymentType(1);p.setAmount(m(net));p.setRefundedAmount(counter);p.setCreatedAt(LocalDateTime.now());
        when(payments.findByOrderIdForUpdate(1L)).thenReturn(List.of(p));when(payments.findByOrderId(1L)).thenReturn(List.of(p));
    }
    @Test void auditApprovalRejectsUnknownFundingBeforeAnyRestockOrCancellation() {
        cashSources("100",null);var applied=pendingCancellation("100","100");
        var command=new com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand();command.setAfterSalesId(50L);command.setApproved(true);
        assertThrows(IllegalArgumentException.class,()->afterSalesService().audit(command,7L));
        assertEquals(AfterSales.STATUS_APPLIED,applied.getStatus());assertEquals(4,order.getOrderStatus());
        verify(afterSales,never()).save(any());verify(inventory,never()).inbound(any(),any());verify(inventory,never()).lockProducts(any(),any());
    }
    @Test void auditApprovalRejectsGrossChildAmountsOnDiscountedCancellation() {
        cashSources("70",m("0"));var applied=pendingCancellation("70","100");
        var command=new com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand();command.setAfterSalesId(50L);command.setApproved(true);
        assertThrows(IllegalArgumentException.class,()->afterSalesService().audit(command,7L));
        assertEquals(AfterSales.STATUS_APPLIED,applied.getStatus());verify(afterSales,never()).save(any());verify(inventory,never()).inbound(any(),any());verify(inventory,never()).lockProducts(any(),any());
    }

    @Test void subStoragePrecisionQuantityIsRejectedByPreviewBeforeAnyMutation() {
        var error=assertThrows(IllegalArgumentException.class,()->service.previewRefund(1L,request("0.00001")));
        assertTrue(error.getMessage().contains("4位"));assertEquals(m("100"),account.getRemainingAmount());
        verify(inventory,never()).inbound(any(),any());verify(inventory,never()).lockProducts(any(),any());verify(afterSales,never()).save(any());
    }
    @Test void subStoragePrecisionQuantityIsRejectedByFormalAdminReturnWithoutFacts() {
        cashSources("100",m("0"));order.setOrderStatus(6);
        var command=new com.agri_system.application.after_sales.dto.command.AdminReturnCommand();command.setOrderId(1L);command.setQuoteId("a".repeat(64));command.setRefundAmount(m("0"));
        var row=new com.agri_system.application.after_sales.dto.command.AdminReturnCommand.ReturnItem();row.setOrderItemId(1L);row.setQuantity(new BigDecimal("0.00001"));row.setRefundAmount(m("0"));command.setItems(List.of(row));
        var error=assertThrows(IllegalArgumentException.class,()->afterSalesService().createFromAdmin(command,7L));
        assertTrue(error.getMessage().contains("4位"));verify(inventory,never()).inbound(any(),any());verify(inventory,never()).lockProducts(any(),any());verify(afterSales,never()).save(any());verify(payments,never()).save(any());
    }
    @Test void halfABoxReturnKeepsPhysicalQuantityAndNetRefundTogether() {
        cashSources("100",m("0"));order.setOrderStatus(6);var original=items.findByOrderId(1L).get(0);original.setQuantity(BigDecimal.ONE);original.setUnit("箱");
        when(afterSales.save(any())).thenAnswer(i->{AfterSales fact=i.getArgument(0);fact.setId(50L);return fact;});
        var quote=service.previewRefund(1L,request("0.500000"));assertEquals(m("50"),quote.refundAmount());
        var command=new com.agri_system.application.after_sales.dto.command.AdminReturnCommand();command.setOrderId(1L);command.setQuoteId(quote.quoteId());command.setRefundAmount(m("50"));
        var row=new com.agri_system.application.after_sales.dto.command.AdminReturnCommand.ReturnItem();row.setOrderItemId(1L);row.setQuantity(new BigDecimal("0.500000"));row.setRefundAmount(m("50"));command.setItems(List.of(row));
        afterSalesService().createFromAdmin(command,7L);
        verify(inventory).inbound(argThat(i->"箱".equals(i.getUnit())&&i.getQuantity().compareTo(new BigDecimal("0.5"))==0),eq(7L));
        verify(payments).save(argThat(i->i.getRefundedAmount().compareTo(m("50"))==0));
    }
    @Test void clientDiscountedCancellationUsesNetChildrenAndConfirmedRefundCanReplay() {
        cashSources("70",m("0"));
        var stored=new java.util.concurrent.atomic.AtomicReference<AfterSales>();
        var details=new java.util.concurrent.atomic.AtomicReference<List<AfterSalesItem>>(List.of());
        when(afterSales.save(any())).thenAnswer(i->{AfterSales fact=i.getArgument(0);fact.setId(50L);stored.set(fact);return fact;});
        when(afterSales.findById(50L)).thenAnswer(i->Optional.ofNullable(stored.get()));
        when(afterSales.findByIdForUpdate(50L)).thenAnswer(i->Optional.ofNullable(stored.get()));
        when(afterSales.findByRefundQuoteIdForUpdate(anyString())).thenAnswer(i->{var fact=stored.get();return fact!=null&&Objects.equals(fact.getRefundQuoteId(),i.getArgument(0))?Optional.of(fact):Optional.empty();});
        when(afterSales.findByOrderId(1L)).thenAnswer(i->stored.get()==null?List.of():List.of(stored.get()));
        when(afterSales.findProcessingByOrderId(1L)).thenAnswer(i->{var fact=stored.get();return fact!=null&&(fact.isPending()||fact.isWaitRefund())?Optional.of(fact):Optional.empty();});
        doAnswer(i->{List<AfterSalesItem> rows=i.getArgument(0);rows.forEach(r->{r.setId(60L);r.setAfterSalesId(50L);});details.set(rows);return null;}).when(afterItems).saveBatch(anyList());
        when(afterItems.findByAfterSalesId(50L)).thenAnswer(i->details.get());
        var afterService=afterSalesService();
        var apply=new com.agri_system.application.after_sales.dto.command.ClientApplyCommand();apply.setOrderId(1L);apply.setReason("未发货取消");
        assertEquals(50L,afterService.applyFromClient(apply,10L));
        assertEquals(m("70"),details.get().get(0).getRefundAmount());assertNull(stored.get().getCashRefundAmount());
        var audit=new com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand();audit.setAfterSalesId(50L);audit.setApproved(true);afterService.audit(audit,7L);
        assertEquals(AfterSales.STATUS_WAIT_REFUND,stored.get().getStatus());assertEquals(7,order.getOrderStatus());
        var preview=new OrderRefundPreviewCommand();preview.setAfterSalesId(50L);var quote=service.previewRefund(1L,preview);
        assertEquals(m("70"),quote.refundAmount());assertEquals(m("70"),quote.cashRefundAmount());
        var refund=new com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand();refund.setAfterSalesId(50L);refund.setQuoteId(quote.quoteId());
        afterService.refund(refund,7L);afterService.refund(refund,7L);
        assertEquals(m("70"),stored.get().getRefundAmount());assertEquals(m("70"),stored.get().getCashRefundAmount());assertEquals(m("70"),details.get().get(0).getRefundAmount());
        assertEquals(AfterSales.STATUS_FINISHED,stored.get().getStatus());assertEquals(8,order.getOrderStatus());
        verify(payments,times(1)).save(any());verify(inventory,times(1)).inbound(any(),eq(7L));
        refund.setRefundAmount(m("69"));assertThrows(IllegalArgumentException.class,()->afterService.refund(refund,7L));
        refund.setRefundAmount(null);assertThrows(IllegalArgumentException.class,()->afterService.refund(refund,8L));
        refund.setQuoteId("b".repeat(64));assertThrows(IllegalArgumentException.class,()->afterService.refund(refund,7L));
        verify(payments,times(1)).save(any());
    }
    @Test void cashDirectFullRefundReplaysOriginalNullDefaultRequestWithoutRebuildingRemainder() {
        cashSources("100",m("0"));
        var product=new com.agri_system.domain.product.model.Product();product.setId(20L);
        var products=(com.agri_system.domain.product.repository.ProductRepository)org.springframework.test.util.ReflectionTestUtils.getField(org.springframework.test.util.ReflectionTestUtils.getField(service,"refundService"),"productRepository");when(products.findById(20L)).thenReturn(Optional.of(product));
        var stored=new java.util.concurrent.atomic.AtomicReference<AfterSales>();
        var details=new java.util.concurrent.atomic.AtomicReference<List<AfterSalesItem>>(List.of());
        when(afterSales.save(any())).thenAnswer(i->{AfterSales fact=i.getArgument(0);fact.setId(50L);stored.set(fact);return fact;});
        when(afterSales.findByRefundQuoteIdForUpdate(anyString())).thenAnswer(i->{var fact=stored.get();return fact!=null&&Objects.equals(fact.getRefundQuoteId(),i.getArgument(0))?Optional.of(fact):Optional.empty();});
        when(afterSales.findByOrderId(1L)).thenAnswer(i->stored.get()==null?List.of():List.of(stored.get()));
        doAnswer(i->{details.set(i.getArgument(0));return null;}).when(afterItems).saveBatch(anyList());
        when(afterItems.findByAfterSalesId(50L)).thenAnswer(i->details.get());
        var quote=service.previewRefund(1L,new OrderRefundPreviewCommand());
        var refund=new OrderRefundCommand();refund.setOrderId(1L);refund.setRefundReason("全退");refund.setQuoteId(quote.quoteId());
        service.refundOrder(refund,7L);service.refundOrder(refund,7L);
        assertEquals(8,order.getOrderStatus());assertEquals(m("100"),stored.get().getCashRefundAmount());
        verify(payments,times(1)).save(any());verify(inventory,times(1)).inbound(any(),eq(7L));verify(afterItems,times(1)).saveBatch(anyList());
        refund.setRefundAmount(m("100"));assertThrows(IllegalArgumentException.class,()->service.refundOrder(refund,7L));
        refund.setRefundAmount(null);assertThrows(IllegalArgumentException.class,()->service.refundOrder(refund,8L));
        refund.setRefundReason("改变原因");assertThrows(IllegalArgumentException.class,()->service.refundOrder(refund,7L));
        verify(payments,times(1)).save(any());
    }

    @Test void previewRejectsSplitBottleUsingOriginalSaleEvidenceBeforeFundingOrInventoryMutation() {
        useRealOriginalQuantityValidation();
        var error = assertThrows(IllegalArgumentException.class, () -> service.previewRefund(1L, request("0.1")));
        assertTrue(error.getMessage().contains("整数"));
        verify(accounts, never()).findByOrderIdForUpdate(anyLong());
        verify(inventory, never()).inbound(any(), anyLong());
        verify(payments, never()).save(any()); verify(afterSales, never()).save(any());
    }

    @Test void previewAllowsHalfBoxWhenOriginalRatioConvertsToSixCompleteBottles() {
        useRealOriginalQuantityValidation();
        var result = service.previewRefund(1L, request("0.5"));
        assertEquals(m("5"), result.refundAmount());
        verify(payments, never()).save(any()); verify(inventory, never()).inbound(any(), anyLong());
    }

    private void useRealOriginalQuantityValidation() {
        var allocationRepo = mock(com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository.class);
        var batchRepo = mock(com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository.class);
        var unitRepo = mock(com.agri_system.domain.product.repository.ProductUnitRepository.class);
        var unitService = new com.agri_system.application.product.service.ProductUnitService(unitRepo,
                mock(com.agri_system.domain.product.repository.ProductRepository.class));
        var base = new com.agri_system.domain.product.model.ProductUnit();
        base.setId(7L); base.setProductId(20L); base.setUnitName("瓶"); base.setIsBaseUnit(1);
        when(unitRepo.findById(7L)).thenReturn(Optional.of(base));
        var batch = new com.agri_system.domain.inventory.model.InventoryReceiptBatch();
        batch.setId(8L); batch.setProductId(20L); batch.setBaseUnitId(7L);
        when(batchRepo.findById(8L)).thenReturn(Optional.of(batch));
        var allocation = new OrderItemBatchAllocation(); allocation.setBatchId(8L); allocation.setSourceMovementId(9L);
        allocation.setAllocatedBaseQuantity(new BigDecimal("120"));
        when(allocationRepo.findByOrderItemId(1L)).thenReturn(List.of(allocation));
        var ledger = new com.agri_system.application.inventory.service.InventoryLedgerService(null,null,null,batchRepo,null,
                unitService,null,allocationRepo,null,null);
        doAnswer(i -> {ledger.validateReturnQuantity(i.getArgument(0), i.getArgument(1)); return null;})
                .when(inventory).validateReturnQuantity(any(), any());
    }

    @Test void unpaidCreditPreviewShowsDebtReductionWithoutCashRefund() {
        var result=service.previewRefund(1L,request("3"));
        assertEquals(m("30"),result.refundAmount());assertEquals(m("30"),result.creditReductionAmount());assertEquals(m("0"),result.cashRefundAmount());
        assertEquals(m("100"),result.remainingDebt());assertTrue(result.sources().isEmpty());
        assertEquals(m("100"),account.getRemainingAmount());
    }
    @Test void repaymentChangesQuoteEvenWhenCurrentReturnStillOnlyReducesDebt() {
        var first=service.previewRefund(1L,request("3"));
        account.setPaidAmount(m("40"));account.setRemainingAmount(m("60"));
        var p=new CreditRepaymentLog();p.setId(21L);p.setCreditAccountId(11L);p.setCustomerId(10L);p.setStoreId(1L);p.setAmount(m("40"));p.setRefundedAmount(m("0"));p.setPaymentMethod(1);p.setCreatedAt(LocalDateTime.now());repaymentSources.add(p);
        var second=service.previewRefund(1L,request("3"));
        assertNotEquals(first.quoteId(),second.quoteId());
        var c=new OrderRefundCommand();c.setOrderId(1L);c.setRefundAmount(m("30"));c.setRefundReason("退货");c.setQuoteId(first.quoteId());
        var i=new OrderRefundCommand.RefundItem();i.setOrderItemId(1L);i.setQuantity(new BigDecimal("3"));i.setRefundAmount(m("30"));c.setItems(List.of(i));
        assertThrows(IllegalArgumentException.class,()->service.refundOrder(c,7L));
        assertEquals(m("60"),account.getRemainingAmount());verify(inventory,never()).inbound(any(),any());verify(inventory,never()).lockProducts(any(),any());
    }
    @Test void creditFormalReturnRequiresConfirmationQuote() {
        var c=new OrderRefundCommand();c.setOrderId(1L);c.setRefundReason("退货");
        var error=assertThrows(IllegalArgumentException.class,()->service.refundOrder(c,7L));
        assertTrue(error.getMessage().contains("预览"));assertEquals(m("100"),account.getRemainingAmount());
    }
    @Test void waitingAfterSalesCannotBorrowAnotherOrdersPreviewExclusion() {
        var c=request("3");c.setAfterSalesId(50L);
        var as=new AfterSales();as.setId(50L);as.setOrderId(2L);as.setStatus(AfterSales.STATUS_WAIT_REFUND);
        when(afterSales.findById(50L)).thenReturn(Optional.of(as));when(afterSales.findByIdForUpdate(50L)).thenReturn(Optional.of(as));
        assertThrows(IllegalArgumentException.class,()->service.previewRefund(1L,c));
    }
}
