package com.agri_system.application.credit.service;

import com.agri_system.application.credit.dto.CreditRepayCommand;
import com.agri_system.application.credit.dto.CreditRepaymentVO;
import com.agri_system.domain.credit.model.*;
import com.agri_system.domain.credit.repository.*;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.model.Order;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FifoRepaymentTest {
    @Mock CreditAccountRepository creditAccountRepository;
    @Mock CreditRepaymentLogRepository repaymentLogRepository;
    @Mock CustomerRepository customerRepository;
    @Mock AfterSalesRepository afterSalesRepository;
    @Mock CreditAccessService accessService;
    @Mock OrderRepository orderRepository;
    @InjectMocks CreditRepaymentService repaymentService;
    List<CreditAccount> accounts;
    @BeforeEach void setup() {
        accounts = List.of(account(1,100),account(2,200),account(3,300));
        when(accessService.resolve(8L,1L)).thenReturn(new CreditAccessService.Scope(8L,1L,false));
        Customer customer=new Customer(); customer.setId(100L); customer.setStatus(1);
        when(customerRepository.findByIdWithLock(100L)).thenReturn(Optional.of(customer));
        when(creditAccountRepository.sumRemainingAmount(100L)).thenReturn(new BigDecimal("600.00"));
        when(creditAccountRepository.findOutstandingAccounts(100L)).thenReturn(accounts);
        when(creditAccountRepository.findOutstandingForUpdate(100L,1L)).thenReturn(accounts);
        for(CreditAccount account:accounts) {
            when(creditAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
            Order order=new Order();order.setId(account.getOrderId());order.setOrderNo("SALE-"+account.getId());
            when(orderRepository.findById(account.getOrderId())).thenReturn(Optional.of(order));
        }
        when(repaymentLogRepository.findByRequestKey(1L,"request-1")).thenReturn(List.of());
        java.util.concurrent.atomic.AtomicLong sourceId = new java.util.concurrent.atomic.AtomicLong(90);
        when(repaymentLogRepository.save(any())).thenAnswer(call -> { CreditRepaymentLog log=call.getArgument(0); log.setId(sourceId.getAndIncrement()); return log; });
    }
    private CreditAccount account(long id,int remaining) {
        CreditAccount a=new CreditAccount(); a.setId(id); a.setOrderId(id+10); a.setStoreId(1L); a.setCustomerId(100L);
        a.setCreditAmount(new BigDecimal(remaining).setScale(2)); a.setPaidAmount(new BigDecimal("0.00"));
        a.setRemainingAmount(new BigDecimal(remaining).setScale(2)); a.setStatus(1); return a;
    }
    private CreditRepayCommand command(String amount) {
        CreditRepayCommand c=new CreditRepayCommand(); c.setCustomerId(100L); c.setStoreId(1L); c.setAmount(new BigDecimal(amount));
        c.setPaymentMethod(1); c.setRequestKey("request-1"); return c;
    }
    @Test void fifoSavesOneSourceForEachOriginalAccountAndReturnsOneBatch() {
        CreditRepaymentVO result=repaymentService.repay(command("400.00"),8L);
        ArgumentCaptor<CreditRepaymentLog> capture=ArgumentCaptor.forClass(CreditRepaymentLog.class);
        verify(repaymentLogRepository,times(3)).save(capture.capture());
        List<CreditRepaymentLog> sources=capture.getAllValues();
        assertEquals(List.of(1L,2L,3L),sources.stream().map(CreditRepaymentLog::getCreditAccountId).toList());
        assertEquals(List.of(new BigDecimal("100.00"),new BigDecimal("200.00"),new BigDecimal("100.00")),sources.stream().map(CreditRepaymentLog::getAmount).toList());
        assertEquals(1,sources.stream().map(CreditRepaymentLog::getRepaymentNo).distinct().count());
        assertEquals("request-1",sources.get(0).getRequestKey());
        assertTrue(sources.subList(1,sources.size()).stream().allMatch(l -> l.getRequestKey()==null));
        assertTrue(sources.stream().allMatch(l -> l.getRefundedAmount().signum()==0 && l.getCreatedBy()==8L));
        assertEquals(new BigDecimal("400.00"),result.getAmount()); assertEquals(3,result.getAllocations().size());
        assertEquals(new BigDecimal("200.00"),accounts.get(2).getRemainingAmount());
        InOrder locks=inOrder(repaymentLogRepository,customerRepository,creditAccountRepository);
        locks.verify(customerRepository).findByIdWithLock(100L);
        locks.verify(creditAccountRepository).findOutstandingForUpdate(100L,1L);
    }
    private CreditRepaymentLog previous(String amount) {
        CreditRepaymentLog l=new CreditRepaymentLog(); l.setId(50L);l.setRepaymentNo("CRPknown");l.setRequestKey("request-1");l.setCustomerId(100L);l.setStoreId(1L);l.setAmount(new BigDecimal(amount));l.setPaymentMethod(1);l.setCreditAccountId(1L);l.setCreatedBy(8L);l.setRefundedAmount(BigDecimal.ZERO); return l;
    }
    @Test void sameRequestReturnsExistingBatchBeforeCheckingCurrentDebt() {
        when(repaymentLogRepository.findByRequestKey(1L,"request-1")).thenReturn(List.of(previous("400.00")));
        when(repaymentLogRepository.findByRepaymentNoForUpdate(1L,"CRPknown")).thenReturn(List.of(previous("400.00")));
        CreditRepaymentVO result=repaymentService.repay(command("400.00"),8L);
        assertEquals("CRPknown",result.getRepaymentNo()); assertEquals(11L,result.getAllocations().get(0).getOrderId()); assertEquals("SALE-1",result.getAllocations().get(0).getOrderNo()); verify(creditAccountRepository,never()).findOutstandingForUpdate(any(),any());
        verify(repaymentLogRepository,never()).save(any());
    }
    @Test void requestCannotBeReusedForDifferentAmount() {
        when(repaymentLogRepository.findByRequestKey(1L,"request-1")).thenReturn(List.of(previous("40.00")));
        when(repaymentLogRepository.findByRepaymentNoForUpdate(1L,"CRPknown")).thenReturn(List.of(previous("40.00")));
        assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("50.00"),8L)); verify(repaymentLogRepository,never()).save(any());
    }
    @Test void requestCannotBeReusedForDifferentCustomer() {
        CreditRepaymentLog log=previous("40.00");log.setCustomerId(99L);
        when(repaymentLogRepository.findByRequestKey(1L,"request-1")).thenReturn(List.of(log));
        assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("40.00"),8L));
    }
    @Test void retryLoadsEveryAllocationFromHeadAndReturnsTheOriginalWholeBatch() {
        CreditRepaymentLog head=previous("100.00");
        CreditRepaymentLog second=previous("200.00");second.setId(51L);second.setCreditAccountId(2L);second.setRequestKey(null);
        CreditRepaymentLog third=previous("100.00");third.setId(52L);third.setCreditAccountId(3L);third.setRequestKey(null);
        when(repaymentLogRepository.findByRequestKey(1L,"request-1")).thenReturn(List.of(head));
        when(repaymentLogRepository.findByRepaymentNoForUpdate(1L,"CRPknown")).thenReturn(List.of(head,second,third));
        CreditRepaymentVO result=assertDoesNotThrow(()->repaymentService.repay(command("400.00"),8L));
        assertEquals("request-1",result.getRequestKey());assertEquals(new BigDecimal("400.00"),result.getAmount());
        assertEquals(List.of(11L,12L,13L),result.getAllocations().stream().map(CreditRepaymentVO.Allocation::getOrderId).toList());
        verify(creditAccountRepository,never()).save(any());verify(repaymentLogRepository,never()).save(any());
    }
    @Test void duplicateRequestClaimFailsBeforeMutatingAnyAccountOrSavingOtherSources() {
        doAnswer(call -> {
            assertEquals(new BigDecimal("0.00"),accounts.get(0).getPaidAmount(),"争用唯一身份前不能先改应收账户");
            assertEquals(new BigDecimal("100.00"),accounts.get(0).getRemainingAmount());
            throw new DuplicateKeyException("uk_credit_store_request");
        }).when(repaymentLogRepository).save(any());
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("400.00"),8L));
        assertTrue(failure.getMessage().contains("请求标识"));
        verify(creditAccountRepository,never()).save(any());verify(repaymentLogRepository,times(1)).save(any());
        assertEquals(new BigDecimal("0.00"),accounts.get(1).getPaidAmount());
    }
    @Test void preciseMoneyAndSupportedOfflineMethodAreRequired() {
        for(String amount:List.of("-1.00","0.00","1.001")) assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command(amount),8L));
        CreditRepayCommand c=command("10.00");c.setPaymentMethod(8);assertThrows(IllegalArgumentException.class,()->repaymentService.repay(c,8L));
        verify(repaymentLogRepository,never()).save(any());
    }
    @Test void emptyRequestAndForgedOperatorAreRejected() {
        CreditRepayCommand c=command("10.00"); c.setRequestKey(" ");assertThrows(IllegalArgumentException.class,()->repaymentService.repay(c,8L));
        CreditRepayCommand forged=command("10.00");forged.setCreatedBy(99L);assertThrows(IllegalArgumentException.class,()->repaymentService.repay(forged,8L));
    }
    @Test void overdueAccountCanBePaidAndOriginalAmountsArePreserved() {
        accounts.get(0).setStatus(3); repaymentService.repay(command("30.00"),8L);
        assertEquals(new BigDecimal("100.00"),accounts.get(0).getCreditAmount());
        assertEquals(new BigDecimal("30.00"),accounts.get(0).getPaidAmount());assertEquals(new BigDecimal("70.00"),accounts.get(0).getRemainingAmount());
    }
    @Test void unknownHistoricalReturnStopsBeforeCollectingMoreMoney() {
        AfterSales history=new AfterSales();history.setStatus(AfterSales.STATUS_FINISHED);history.setRefundAmount(new BigDecimal("20.00"));
        when(afterSalesRepository.findByOrderId(11L)).thenReturn(List.of(history));
        assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("30.00"),8L));verify(repaymentLogRepository,never()).save(any());
    }
    @Test void knownReturnReductionParticipatesInBalanceInvariant() {
        accounts.get(0).setRemainingAmount(new BigDecimal("70.00"));
        AfterSales history=new AfterSales();history.setStatus(AfterSales.STATUS_FINISHED);history.setCreditReductionAmount(new BigDecimal("30.00"));history.setCashRefundAmount(BigDecimal.ZERO);history.setRefundAmount(new BigDecimal("30.00"));
        when(afterSalesRepository.findByOrderId(11L)).thenReturn(List.of(history));
        repaymentService.repay(command("50.00"),8L);assertEquals(new BigDecimal("20.00"),accounts.get(0).getRemainingAmount());
    }
    @Test void inconsistentBalanceOrCrossStoreAccountCannotBeCollected() {
        accounts.get(0).setStoreId(2L);assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("10.00"),8L));
        accounts.get(0).setStoreId(1L);accounts.get(0).setRemainingAmount(new BigDecimal("90.00"));assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("10.00"),8L));
        verify(repaymentLogRepository,never()).save(any());
    }
    @Test void historicPaidAmountWithoutAllocatedSourcesCannotReceiveMoreMoney() {
        accounts.get(0).setPaidAmount(new BigDecimal("40.00"));accounts.get(0).setRemainingAmount(new BigDecimal("60.00"));
        assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("10.00"),8L));verify(repaymentLogRepository,never()).save(any());
    }
    @Test void knownRepaymentSourcesMustMatchPaidAmountAndHaveValidCounters() {
        accounts.get(0).setPaidAmount(new BigDecimal("40.00"));accounts.get(0).setRemainingAmount(new BigDecimal("60.00"));
        CreditRepaymentLog source=previous("40.00");source.setRefundedAmount(null);
        when(repaymentLogRepository.findByCreditAccountIdForUpdate(1L)).thenReturn(List.of(source));
        assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("10.00"),8L));
        source.setRefundedAmount(new BigDecimal("41.00"));assertThrows(IllegalArgumentException.class,()->repaymentService.repay(command("10.00"),8L));
        source.setRefundedAmount(BigDecimal.ZERO);repaymentService.repay(command("10.00"),8L);
        assertEquals(new BigDecimal("50.00"),accounts.get(0).getPaidAmount());
    }

}
