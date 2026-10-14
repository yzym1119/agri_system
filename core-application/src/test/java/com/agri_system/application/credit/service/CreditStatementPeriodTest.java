package com.agri_system.application.credit.service;

import com.agri_system.application.credit.dto.CreditStatementGenerateCommand;
import com.agri_system.domain.credit.model.*;
import com.agri_system.domain.credit.repository.*;
import com.agri_system.domain.credit.service.CreditDomainService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class CreditStatementPeriodTest {
    @Mock CreditAccountRepository accountRepository;@Mock CreditRepaymentLogRepository repaymentLogRepository;
    @Mock CreditStatementRepository statementRepository;@Mock CreditDomainService creditDomainService;
    @Mock CustomerRepository customerRepository;@Mock OrderRepository orderRepository;@Mock AfterSalesRepository afterSalesRepository;
    @Mock CreditAccessService accessService;@InjectMocks CreditApplicationService service;
    List<CreditAccount> accounts;List<CreditRepaymentLog> repayments;AfterSales returnInPeriod;
    private LocalDateTime time(String date){return LocalDate.parse(date).atTime(12,0);}
    private CreditAccount account(long id,String date,String credit,String paid,String left){CreditAccount a=new CreditAccount();a.setId(id);a.setOrderId(id+10);a.setCustomerId(100L);a.setStoreId(1L);a.setCreatedAt(time(date));a.setCreditAmount(new BigDecimal(credit));a.setPaidAmount(new BigDecimal(paid));a.setRemainingAmount(new BigDecimal(left));return a;}
    private CreditRepaymentLog repayment(long id,long accountId,String date,String amount,String refunded){CreditRepaymentLog l=new CreditRepaymentLog();l.setId(id);l.setCreditAccountId(accountId);l.setCustomerId(100L);l.setStoreId(1L);l.setCreatedAt(time(date));l.setAmount(new BigDecimal(amount));l.setRefundedAmount(new BigDecimal(refunded));return l;}
    private AfterSales returned(String date,String reduction,String cash){AfterSales a=new AfterSales();a.setOrderId(11L);a.setStatus(AfterSales.STATUS_FINISHED);a.setRefundTime(time(date));a.setCreditReductionAmount(new BigDecimal(reduction));a.setCashRefundAmount(new BigDecimal(cash));a.setRefundAmount(a.getCreditReductionAmount().add(a.getCashRefundAmount()));return a;}
    @BeforeEach void setup(){
        when(accessService.resolve(8L,1L)).thenReturn(new CreditAccessService.Scope(8L,1L,false));
        Customer customer=new Customer();customer.setId(100L);when(customerRepository.findByIdWithLock(100L)).thenReturn(Optional.of(customer));
        accounts=List.of(account(1,"2026-01-01","100.00","60.00","0.00"),account(2,"2026-02-10","80.00","30.00","50.00"),account(3,"2026-03-05","50.00","0.00","50.00"));
        repayments=List.of(repayment(1,1,"2026-01-05","20.00","0.00"),repayment(2,1,"2026-02-15","40.00","15.00"),repayment(3,2,"2026-02-18","10.00","0.00"),repayment(4,2,"2026-03-02","20.00","0.00"));
        returnInPeriod=returned("2026-02-20","40.00","0.00");
        when(accountRepository.listAccounts(100L,null,1L,null,null)).thenReturn(accounts);
        when(accountRepository.listAccounts(100L,null,1L,LocalDate.parse("2026-02-01"),LocalDate.parse("2026-02-28"))).thenReturn(List.of(accounts.get(1)));
        when(repaymentLogRepository.listByCustomer(100L,1L,null,null)).thenReturn(repayments);
        when(repaymentLogRepository.listByCustomer(100L,1L,LocalDate.parse("2026-02-01"),LocalDate.parse("2026-02-28"))).thenReturn(List.of(repayments.get(1),repayments.get(2)));
        when(afterSalesRepository.findByOrderId(11L)).thenReturn(List.of(returnInPeriod,returned("2026-02-22","0.00","10.00"),returned("2026-03-02","0.00","5.00")));
        when(accountRepository.sumRemainingAmount(100L,1L)).thenReturn(new BigDecimal("100.00"));
        when(statementRepository.save(any())).thenAnswer(call->call.getArgument(0));
    }
    private CreditStatementGenerateCommand command(){CreditStatementGenerateCommand c=new CreditStatementGenerateCommand();c.setCustomerId(100L);c.setStoreId(1L);c.setStartDate(LocalDate.parse("2026-02-01"));c.setEndDate(LocalDate.parse("2026-02-28"));return c;}
    @Test void periodStartsWithOldDebtAndIgnoresLaterRepaymentNewDebtAndRefund(){
        CreditStatement result=service.generateStatement(command(),8L);
        assertEquals(new BigDecimal("80.00"),result.getTotalCreditAmount());assertEquals(new BigDecimal("50.00"),result.getTotalPaidAmount());
        assertEquals(new BigDecimal("70.00"),result.getTotalRemainingAmount());
        assertTrue(result.getRemark().contains("期初欠款=80.00"));assertTrue(result.getRemark().contains("本期冲欠款=40.00"));assertTrue(result.getRemark().contains("本期退已收款=10.00"));assertTrue(result.getRemark().contains("2026-02-28"));
        verify(accountRepository,never()).sumRemainingAmount(any(),any());
    }
    @Test void historicRepaymentWithoutOriginalAllocationDoesNotProduceApparentStatement(){
        repayments.get(0).setCreditAccountId(null);assertThrows(IllegalArgumentException.class,()->service.generateStatement(command(),8L));verify(statementRepository,never()).save(any());
    }
    @Test void unknownCompletedReturnTimeOrSplitStopsStatement(){
        returnInPeriod.setRefundTime(null);assertThrows(IllegalArgumentException.class,()->service.generateStatement(command(),8L));
        returnInPeriod.setRefundTime(time("2026-02-20"));returnInPeriod.setCreditReductionAmount(null);assertThrows(IllegalArgumentException.class,()->service.generateStatement(command(),8L));verify(statementRepository,never()).save(any());
    }
    @Test void originalAggregatePaidMustHaveFullyTraceableSourcesAndCurrentBalanceMustAgree(){
        accounts.get(0).setPaidAmount(new BigDecimal("65.00"));assertThrows(IllegalArgumentException.class,()->service.generateStatement(command(),8L));
        accounts.get(0).setPaidAmount(new BigDecimal("60.00"));accounts.get(0).setRemainingAmount(BigDecimal.TEN);assertThrows(IllegalArgumentException.class,()->service.generateStatement(command(),8L));verify(statementRepository,never()).save(any());
    }
}
