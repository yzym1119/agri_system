package com.agri_system.application.credit.service;

import com.agri_system.application.credit.dto.CreditRepayCommand;
import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.model.CreditRepaymentLog;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FifoRepaymentTest {

    @Mock
    private CreditAccountRepository creditAccountRepository;

    @Mock
    private CreditRepaymentLogRepository repaymentLogRepository;

    @InjectMocks
    private CreditRepaymentService repaymentService;

    private CreditAccount accountA;
    private CreditAccount accountB;
    private CreditAccount accountC;

    @BeforeEach
    void setUp() {
        accountA = new CreditAccount();
        accountA.setId(1L);
        accountA.setCustomerId(100L);
        accountA.setRemainingAmount(new BigDecimal("100"));
        accountA.setPaidAmount(BigDecimal.ZERO);
        accountA.setStatus(1);

        accountB = new CreditAccount();
        accountB.setId(2L);
        accountB.setCustomerId(100L);
        accountB.setRemainingAmount(new BigDecimal("200"));
        accountB.setPaidAmount(BigDecimal.ZERO);
        accountB.setStatus(1);

        accountC = new CreditAccount();
        accountC.setId(3L);
        accountC.setCustomerId(100L);
        accountC.setRemainingAmount(new BigDecimal("300"));
        accountC.setPaidAmount(BigDecimal.ZERO);
        accountC.setStatus(1);
    }

    @Test
    void testFifoRepayment() {
        CreditRepayCommand command = new CreditRepayCommand();
        command.setCustomerId(100L);
        command.setStoreId(1L);
        command.setAmount(new BigDecimal("400"));
        command.setPaymentMethod(1);

        when(creditAccountRepository.sumRemainingAmount(100L)).thenReturn(new BigDecimal("600"));
        List<CreditAccount> accounts = Arrays.asList(accountA, accountB, accountC);
        when(creditAccountRepository.findOutstandingAccounts(100L)).thenReturn(accounts);
        when(repaymentLogRepository.save(any(CreditRepaymentLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        repaymentService.repay(command);

        ArgumentCaptor<CreditAccount> captor = ArgumentCaptor.forClass(CreditAccount.class);
        verify(creditAccountRepository, times(3)).save(captor.capture());
        List<CreditAccount> savedAccounts = captor.getAllValues();

        CreditAccount savedA = savedAccounts.get(0);
        assertEquals(BigDecimal.ZERO, savedA.getRemainingAmount());
        assertEquals(new BigDecimal("100"), savedA.getPaidAmount());
        assertEquals(2, savedA.getStatus());

        CreditAccount savedB = savedAccounts.get(1);
        assertEquals(BigDecimal.ZERO, savedB.getRemainingAmount());
        assertEquals(new BigDecimal("200"), savedB.getPaidAmount());
        assertEquals(2, savedB.getStatus());

        CreditAccount savedC = savedAccounts.get(2);
        assertEquals(new BigDecimal("200"), savedC.getRemainingAmount());
        assertEquals(new BigDecimal("100"), savedC.getPaidAmount());
        assertEquals(1, savedC.getStatus());
    }
}






