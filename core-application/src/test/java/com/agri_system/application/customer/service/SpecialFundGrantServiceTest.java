package com.agri_system.application.customer.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 手动发放：余额与权益台账由同一应用服务写入，任一失败都不留下半截事实。 */
@ExtendWith(MockitoExtension.class)
class SpecialFundGrantServiceTest {

    @Mock private WalletApplicationService walletService;
    @Mock private CustomerBenefitLogRepository benefitLogRepository;

    private SpecialFundGrantService service() {
        return new SpecialFundGrantService(walletService, benefitLogRepository);
    }

    @Test
    void writesWalletThenBenefitLogWithOriginalRequestFacts() {
        when(walletService.addSpecialFund(eq(9L), any(BigDecimal.class), eq("MANUAL_GRANT"),
                isNull(), eq(7L), eq("活动补发"))).thenReturn(new BigDecimal("110.00"));
        when(benefitLogRepository.save(any(CustomerBenefitLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal balance = service().grant(9L, new BigDecimal("100.00"), 7L, "活动补发");

        assertThat(balance).isEqualByComparingTo("110.00");
        InOrder order = inOrder(walletService, benefitLogRepository);
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
    }

    @Test
    void walletFailureSkipsBenefitLog() {
        when(walletService.addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any()))
                .thenThrow(new IllegalArgumentException("发放金额非法"));

        assertThatThrownBy(() -> service().grant(9L, new BigDecimal("0"), 7L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(benefitLogRepository);
    }

    @Test
    void benefitLogFailurePropagatesSoTransactionRollsBack() {
        when(walletService.addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any()))
                .thenReturn(new BigDecimal("110.00"));
        when(benefitLogRepository.save(any(CustomerBenefitLog.class)))
                .thenThrow(new IllegalStateException("台账写入失败"));

        assertThatThrownBy(() -> service().grant(9L, new BigDecimal("100.00"), 7L, "活动补发"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("台账写入失败");
        verify(walletService).addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any());
    }
}
