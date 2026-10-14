package com.agri_system.application.credit.service;
import com.agri_system.domain.credit.model.CreditAccount;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class CreditAccountSettlementStatusTest {
    private CreditAccount account(LocalDate dueDate,int status){CreditAccount a=new CreditAccount();a.setCreditAmount(new BigDecimal("100.00"));a.setPaidAmount(BigDecimal.ZERO);a.setRemainingAmount(new BigDecimal("100.00"));a.setDueDate(dueDate);a.setStatus(status);return a;}
    @Test void partiallyPaidOverdueDebtKeepsOverdueStatus(){CreditAccount a=account(LocalDate.now().minusDays(1),3);a.applyRepayment(new BigDecimal("30.00"));assertEquals(3,a.getStatus());assertEquals(new BigDecimal("70.00"),a.getRemainingAmount());}
    @Test void unknownHistoricalDueDateDoesNotInventAReleaseFromOverdue(){CreditAccount a=account(null,3);a.applyRepayment(BigDecimal.TEN);assertEquals(3,a.getStatus());assertNull(a.getDueDate());}
    @Test void clearedOverdueAccountIsClosedAndNonOverduePartialAccountStaysOpen(){CreditAccount a=account(LocalDate.now().minusDays(1),3);a.applyRepayment(new BigDecimal("100.00"));assertEquals(2,a.getStatus());CreditAccount b=account(LocalDate.now().plusDays(1),1);b.applyRepayment(BigDecimal.TEN);assertEquals(1,b.getStatus());}
}
