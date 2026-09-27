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
