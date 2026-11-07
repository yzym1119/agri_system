package com.agri_system.application.customer.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.SpecialFundGrantRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 手动发放：余额与权益台账由同一应用服务写入，任一失败都不留下半截事实；requestKey 提供持久请求幂等。 */
@ExtendWith(MockitoExtension.class)
class SpecialFundGrantServiceTest {

    @Mock private WalletApplicationService walletService;
    @Mock private CustomerBenefitLogRepository benefitLogRepository;
    @Mock private SpecialFundGrantRequestRepository grantRequestRepository;

    private SpecialFundGrantService service() {
        return new SpecialFundGrantService(walletService, benefitLogRepository, grantRequestRepository);
    }

    private void stubPending() {
        when(grantRequestRepository.find(eq("rk-1"), eq(true))).thenReturn(Optional.of(
                new SpecialFundGrantRequestRepository.GrantRequest("rk-1", 9L, new BigDecimal("100.00"), null, "PENDING")));
    }

    @Test
    void writesWalletThenBenefitLogWithOriginalRequestFacts() {
        stubPending();
        when(walletService.addSpecialFund(eq(9L), any(BigDecimal.class), eq("MANUAL_GRANT"),
                isNull(), eq(7L), eq("活动补发"))).thenReturn(new BigDecimal("110.00"));
        when(benefitLogRepository.save(any(CustomerBenefitLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal balance = service().grant(9L, new BigDecimal("100.00"), 7L, "活动补发", "rk-1");

        assertThat(balance).isEqualByComparingTo("110.00");
        InOrder order = inOrder(walletService, benefitLogRepository, grantRequestRepository);
        order.verify(grantRequestRepository).reserve(eq("rk-1"), eq(9L), argThat(a -> a.compareTo(new BigDecimal("100.00")) == 0));
        order.verify(walletService).addSpecialFund(eq(9L), argThat(a -> a.compareTo(new BigDecimal("100.00")) == 0),
                eq("MANUAL_GRANT"), isNull(), eq(7L), eq("活动补发"));
        ArgumentCaptor<CustomerBenefitLog> captor = ArgumentCaptor.forClass(CustomerBenefitLog.class);
        order.verify(benefitLogRepository).save(captor.capture());
        CustomerBenefitLog log = captor.getValue();
        assertThat(log.getCustomerId()).isEqualTo(9L);
        assertThat(log.getSourceType()).isEqualTo(3);
        assertThat(log.getBenefitContent()).isEqualTo("专用金¥100.00");
        assertThat(log.getAssociatedId()).isNull();
        assertThat(log.getCreatedBy()).isEqualTo(7L);
        assertThat(log.getCreatedAt()).isNotNull();
        order.verify(grantRequestRepository).complete(eq("rk-1"), argThat(a -> a.compareTo(new BigDecimal("110.00")) == 0));
    }

    @Test
    void completedRequestReplaysRecordedBalanceWithoutDoubleGrant() {
        when(grantRequestRepository.find(eq("rk-1"), eq(true))).thenReturn(Optional.of(
                new SpecialFundGrantRequestRepository.GrantRequest("rk-1", 9L, new BigDecimal("100.00"),
                        new BigDecimal("110.00"), "COMPLETED")));

        BigDecimal balance = service().grant(9L, new BigDecimal("100.00"), 7L, "活动补发", "rk-1");

        assertThat(balance).isEqualByComparingTo("110.00");
        verifyNoInteractions(walletService, benefitLogRepository);
    }

    @Test
    void missingOrOversizedRequestKeyIsRejected() {
        assertThatThrownBy(() -> service().grant(9L, new BigDecimal("100.00"), 7L, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().grant(9L, new BigDecimal("100.00"), 7L, null, "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(grantRequestRepository, walletService, benefitLogRepository);
    }

    @Test
    void walletFailureSkipsBenefitLog() {
        stubPending();
        when(walletService.addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any()))
                .thenThrow(new IllegalArgumentException("发放金额非法"));

        assertThatThrownBy(() -> service().grant(9L, new BigDecimal("0"), 7L, null, "rk-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(benefitLogRepository);
        verify(grantRequestRepository, never()).complete(anyString(), any());
    }

    @Test
    void benefitLogFailurePropagatesSoTransactionRollsBack() {
        stubPending();
        when(walletService.addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any()))
                .thenReturn(new BigDecimal("110.00"));
        when(benefitLogRepository.save(any(CustomerBenefitLog.class)))
                .thenThrow(new IllegalStateException("台账写入失败"));

        assertThatThrownBy(() -> service().grant(9L, new BigDecimal("100.00"), 7L, "活动补发", "rk-1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("台账写入失败");
        verify(walletService).addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any());
        verify(grantRequestRepository, never()).complete(anyString(), any());
    }
}
