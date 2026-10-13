package com.agri_system.application.order.service;

import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.domain.order.model.OrderItemPriceAudit;
import com.agri_system.domain.order.repository.OrderItemPriceAuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualPriceAuditServiceTest {

    @Mock
    private OrderItemPriceAuditRepository auditRepository;

    @InjectMocks
    private ManualPriceAuditService auditService;

    @Test
    void manualPriceRecordsQuoteDifferenceAndReasonButReturnsTheChosenDealPrice() {
        PriceQuote quote = quote("220");
        givenGeneratedId(99L);

        Long id = auditService.record(quote, new BigDecimal("205"), "老客户议价", 7L, 7L);

        assertThat(id).isPositive();
        OrderItemPriceAudit row = capturedAudit();
        assertThat(row.getOrderItemId()).isEqualTo(55L);
        assertThat(row.getProductId()).isEqualTo(1L);
        assertThat(row.getSaleUnitId()).isEqualTo(11L);
        assertThat(row.getSuggestedUnitPrice()).isEqualByComparingTo("220");
        assertThat(row.getFinalUnitPrice()).isEqualByComparingTo("205");
        assertThat(row.getPriceDifference()).isEqualByComparingTo("-15");
        assertThat(row.getChangeReason()).isEqualTo("老客户议价");
        assertThat(row.getPriceRuleVersion()).isEqualTo("v1");
        assertThat(row.getOperatorId()).isEqualTo(7L);
        assertThat(row.getConfirmerId()).isEqualTo(7L);
        assertThat(row.getQuoteId()).isEqualTo(quote.quoteId());
        assertThat(row.getDecidedAt()).isNotNull();
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsQuoteWithoutOrderItemId() {
        assertThatThrownBy(() -> auditService.record(
                quote("220", null), new BigDecimal("205"), "老客户议价", 7L, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditRepository);
    }

    @Test
    void rejectsMissingOrNegativeDealPrice() {
        assertThatThrownBy(() -> auditService.record(
                quote("220"), null, "老客户议价", 7L, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record(
                quote("220"), new BigDecimal("-0.01"), "老客户议价", 7L, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditRepository);
    }

    @Test
    void rejectsBelowReferenceFloorWithoutConfirmer() {
        assertThatThrownBy(() -> auditService.record(
                belowFloorQuote(), new BigDecimal("10"), "清仓议价", 7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("低于参考底价需确认人");
        verifyNoInteractions(auditRepository);
    }

    @Test
    void belowReferenceFloorWithConfirmerKeepsTheChosenDealPrice() {
        PriceQuote quote = belowFloorQuote();
        givenGeneratedId(100L);

        Long id = auditService.record(quote, new BigDecimal("9"), "清仓议价", 7L, 8L);

        assertThat(id).isPositive();
        OrderItemPriceAudit row = capturedAudit();
        assertThat(row.getSuggestedUnitPrice()).isEqualByComparingTo("10");
        assertThat(row.getFinalUnitPrice()).isEqualByComparingTo("9");
        assertThat(row.getPriceDifference()).isEqualByComparingTo("-1");
        assertThat(row.getOperatorId()).isEqualTo(7L);
        assertThat(row.getConfirmerId()).isEqualTo(8L);
        assertThat(row.getQuoteId()).isEqualTo(quote.quoteId());
    }

    @Test
    void finalDealBelowFloorRequiresConfirmerEvenWhenSuggestedPriceIsAboveFloor() {
        assertThatThrownBy(() -> auditService.record(quote("220"), new BigDecimal("170"), "议价", 7L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("确认人");
        verifyNoInteractions(auditRepository);
    }

    @Test
    void priceChangeRequiresExplicitReason() {
        assertThatThrownBy(() -> auditService.record(quote("220"), new BigDecimal("205"), " ", 7L, 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("原因");
        verifyNoInteractions(auditRepository);
    }

    @Test
    void employeeWithoutNegotiationPermissionCannotChangePrice() {
        withEmployee(7L, false, () -> assertThatThrownBy(() ->
                auditService.validateChange(quote("220"), new BigDecimal("205"), "议价", 7L))
                .isInstanceOf(cn.dev33.satoken.exception.NotPermissionException.class));
        verifyNoInteractions(auditRepository);
    }

    @Test
    void authorizedEmployeeCanNegotiateBelowFloorWithReason() {
        withEmployee(7L, true, () -> auditService.validateChange(quote("220"), new BigDecimal("170"), "清仓议价", 7L));
    }

    @Test
    void cannotBorrowAnotherEmployeesIdentityForNegotiation() {
        withEmployee(8L, true, () -> assertThatThrownBy(() ->
                auditService.validateChange(quote("220"), new BigDecimal("205"), "议价", 7L))
                .hasMessageContaining("当前登录员工"));
    }

    @Test
    void wholeOrderDiscountCannotBypassNegotiationPermission() {
        withEmployee(7L, false, () -> assertThatThrownBy(() ->
                auditService.validateDiscount(new BigDecimal("9.00"), "优惠", 7L))
                .isInstanceOf(cn.dev33.satoken.exception.NotPermissionException.class));
    }

    @Test
    void packageAllocationAuditKeepsZeroDealAndTwelveDecimalDifference() {
        PriceQuote quote = new PriceQuote("package-quote",55L,1L,10L,"POS",null,null,
                "瓶",12L,LocalDate.now(),new BigDecimal("1.428571428571"),"PACKAGE",null,false,false);
        givenGeneratedId(101L);
        auditService.recordAllocation(quote,BigDecimal.ZERO,"套餐议价",7L);
        OrderItemPriceAudit row = capturedAudit();
        assertThat(row.getFinalUnitPrice()).isEqualByComparingTo("0");
        assertThat(row.getPriceDifference()).isEqualByComparingTo("-1.428571428571");
        assertThat(row.getQuoteId()).isEqualTo("package-quote");
        assertThat(row.getSaleUnitId()).isEqualTo(12L);
    }

    @Test
    void packageAllocationCannotOmitOrderLineOrNegotiationReason() {
        assertThatThrownBy(() -> auditService.recordAllocation(quote("220",null),BigDecimal.ZERO,"套餐议价",7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.recordAllocation(quote("220"),BigDecimal.ZERO," ",7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditRepository);
    }

    private void withEmployee(Long id, boolean permitted, Runnable action) {
        var previous = cn.dev33.satoken.SaManager.getStpInterface();
        com.agri_system.application.support.SaTokenTestContext.setUp();
        cn.dev33.satoken.SaManager.setStpInterface(new cn.dev33.satoken.stp.StpInterface() {
            public java.util.List<String> getPermissionList(Object loginId, String type) {
                return permitted ? java.util.List.of("order:price:override") : java.util.List.of();
            }
            public java.util.List<String> getRoleList(Object loginId, String type) { return java.util.List.of(); }
        });
        try {
            cn.dev33.satoken.stp.StpUtil.login("STAFF_" + id);
            action.run();
        } finally {
            cn.dev33.satoken.stp.StpUtil.logout();
            cn.dev33.satoken.SaManager.setStpInterface(previous);
            com.agri_system.application.support.SaTokenTestContext.tearDown();
        }
    }

    private PriceQuote quote(String price) {
        return quote(price, 55L);
    }

    private PriceQuote quote(String price, Long orderItemId) {
        return new PriceQuote("quote-1", orderItemId, 1L, 10L, "POS", null, null,
                "箱", 11L, LocalDate.now(), new BigDecimal(price), "v1",
                new BigDecimal("180.00"), false, false);
    }

    private PriceQuote belowFloorQuote() {
        return new PriceQuote("quote-2", 55L, 1L, 10L, "POS", null, null,
                "瓶", 12L, LocalDate.now(), new BigDecimal("10"), "v1",
                new BigDecimal("12.00"), true, false);
    }

    private void givenGeneratedId(long id) {
        when(auditRepository.save(any(OrderItemPriceAudit.class))).thenAnswer(invocation -> {
            OrderItemPriceAudit row = invocation.getArgument(0);
            row.setId(id);
            return row;
        });
    }

    private OrderItemPriceAudit capturedAudit() {
        ArgumentCaptor<OrderItemPriceAudit> captor = ArgumentCaptor.forClass(OrderItemPriceAudit.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }
}
