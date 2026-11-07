package com.agri_system.application.wallet.service;

import com.agri_system.application.customer.dto.GrantSpecialFundCommand;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 手动发专用金：非法金额必须在写钱包/流水之前拒绝，合法发放仍记录余额与台账。 */
@ExtendWith(MockitoExtension.class)
class SpecialFundGrantValidationTest {

    @Mock private CustomerWalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private WalletAuthCodeRepository walletAuthCodeRepository;

    private WalletApplicationService service() {
        return new WalletApplicationService(walletRepository, transactionRepository, walletAuthCodeRepository);
    }

    @Test
    void rejectsNullCustomerAndInvalidAmountsBeforeAnyWrite() {
        WalletApplicationService service = service();
        List<BigDecimal> invalid = List.of(
                new BigDecimal("0"), new BigDecimal("-1.00"), new BigDecimal("0.001"),
                new BigDecimal("1.005"), new BigDecimal("5000.01"), new BigDecimal("10000.01"),
                new BigDecimal("999999999.99"));
        assertThatThrownBy(() -> service.addSpecialFund(null, BigDecimal.ONE, "MANUAL_GRANT", null, 7L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("客户ID");
        assertThatThrownBy(() -> service.addSpecialFund(9L, null, "MANUAL_GRANT", null, 7L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("发放金额");
        for (BigDecimal amount : invalid) {
            assertThatThrownBy(() -> service.addSpecialFund(9L, amount, "MANUAL_GRANT", null, 7L, null))
                    .as("金额 %s 应被拒绝", amount)
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("发放金额");
        }
        verifyNoInteractions(walletRepository, transactionRepository, walletAuthCodeRepository);
    }

    @Test
    void acceptsBoundaryAmountAndKeepsBalanceAndTransactionFacts() {
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(9L, 2)).thenReturn(Optional.empty());
        when(walletRepository.save(any(CustomerWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal result = service().addSpecialFund(9L, new BigDecimal("5000.00"),
                "MANUAL_GRANT", null, 7L, "活动补发");

        assertThat(result).isEqualByComparingTo("5000.00");
        verify(walletRepository, times(2)).save(any(CustomerWallet.class));
        var captor = org.mockito.ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(captor.capture());
        WalletTransaction transaction = captor.getValue();
        assertThat(transaction.getWalletType()).isEqualTo(2);
        assertThat(transaction.getTransactionType()).isEqualTo(4);
        assertThat(transaction.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(transaction.getBalanceBefore()).isEqualByComparingTo("0");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("5000.00");
        assertThat(transaction.getReferenceType()).isEqualTo("MANUAL_GRANT");
        assertThat(transaction.getRemark()).isEqualTo("活动补发");
    }

    @Test
    void commandValidationRejectsMissingOrMalformedInput() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            GrantSpecialFundCommand blank = new GrantSpecialFundCommand();
            assertThat(fieldNames(validator.validate(blank))).contains("customerId", "amount");

            GrantSpecialFundCommand bad = new GrantSpecialFundCommand();
            bad.setCustomerId(9L);
            bad.setAmount(new BigDecimal("0.001"));
            bad.setRemark("x".repeat(501));
            assertThat(fieldNames(validator.validate(bad))).contains("amount", "remark");

            GrantSpecialFundCommand noKey = new GrantSpecialFundCommand();
            noKey.setCustomerId(9L);
            noKey.setAmount(new BigDecimal("1.00"));
            assertThat(fieldNames(validator.validate(noKey))).contains("requestKey");

            GrantSpecialFundCommand good = new GrantSpecialFundCommand();
            good.setCustomerId(9L);
            good.setAmount(new BigDecimal("1.00"));
            good.setRemark("正常备注");
            good.setRequestKey("rk-1");
            assertThat(validator.validate(good)).isEmpty();
        }
    }

    private static Set<String> fieldNames(Set<ConstraintViolation<GrantSpecialFundCommand>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }
}
