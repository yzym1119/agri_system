package com.agri_system.application.credit.service;
import com.agri_system.application.credit.dto.*;
import com.agri_system.domain.credit.model.*;
import com.agri_system.domain.credit.repository.*;
import com.agri_system.domain.credit.service.CreditDomainService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class CreditQueryFactsTest {
    @Mock CreditAccountRepository accountRepository;@Mock CreditRepaymentLogRepository repaymentLogRepository;
    @Mock CreditStatementRepository statementRepository;@Mock CreditDomainService creditDomainService;
    @Mock CustomerRepository customerRepository;@Mock OrderRepository orderRepository;@Mock AfterSalesRepository afterSalesRepository;
    @Mock CreditAccessService accessService;@InjectMocks CreditApplicationService service;
    @BeforeEach void setup(){when(accessService.resolve(8L,1L)).thenReturn(new CreditAccessService.Scope(8L,1L,false));}
    @Test void storeDebtAndGlobalAvailableCreditUseDifferentScopeWithoutOtherStoreDetails(){
        Customer c=new Customer();c.setCreditLimit(new BigDecimal("100.00"));when(customerRepository.findById(100L)).thenReturn(Optional.of(c));
        when(accountRepository.sumRemainingAmount(100L,1L)).thenReturn(new BigDecimal("30.00"));
        when(accountRepository.sumRemainingAmountAllStores(100L)).thenReturn(new BigDecimal("80.00"));
        CreditDebtVO result=service.statistics(100L,1L,8L);
        assertEquals(1L,result.getStoreId());assertEquals(new BigDecimal("30.00"),result.getTotalDebt());assertEquals(new BigDecimal("20.00"),result.getRemainingCredit());
        verify(accountRepository,never()).listAccounts(any(),any());
    }
    private CreditAccount account(long id){CreditAccount a=new CreditAccount();a.setId(id);a.setCustomerId(100L);a.setStoreId(1L);a.setOrderId(id+10);a.setCreditAmount(new BigDecimal("100.00"));a.setPaidAmount(BigDecimal.ZERO);a.setRemainingAmount(new BigDecimal("100.00"));return a;}
    private CreditRepaymentLog source(long id,long accountId,String amount){CreditRepaymentLog l=new CreditRepaymentLog();l.setId(id);l.setCreditAccountId(accountId);l.setRepaymentNo("CRPone");l.setRequestKey("request-one");l.setCustomerId(100L);l.setStoreId(1L);l.setAmount(new BigDecimal(amount));l.setRefundedAmount(BigDecimal.ZERO);l.setPaymentMethod(1);return l;}
    @Test void batchListUsesSameDeterministicHeadAndAllocationOrderAsRetry(){
        CreditRepaymentLog first=source(90,1,"100.00"),second=source(91,2,"200.00");
        second.setRequestKey(null);
        when(repaymentLogRepository.listByCustomer(100L,1L,null,null)).thenReturn(List.of(second,first));
        for(long id:List.of(1L,2L)){CreditAccount a=account(id);when(accountRepository.findById(id)).thenReturn(Optional.of(a));Order o=new Order();o.setOrderNo("SALE-"+id);when(orderRepository.findById(id+10)).thenReturn(Optional.of(o));}
        List<CreditRepaymentVO> result=service.listRepayments(100L,1L,8L);assertEquals(1,result.size());
        CreditRepaymentVO batch=result.get(0);assertEquals(90L,batch.getId());assertEquals(new BigDecimal("300.00"),batch.getAmount());
        assertEquals(List.of(90L,91L),batch.getAllocations().stream().map(CreditRepaymentVO.Allocation::getId).toList());
        assertEquals(List.of(11L,12L),batch.getAllocations().stream().map(CreditRepaymentVO.Allocation::getOrderId).toList());
        assertEquals("request-one",batch.getRequestKey());
    }
    @Test void unallocatedLegacyRowsWithTheSameNumberRemainSeparateUnknownFacts(){
        CreditRepaymentLog first=source(90,1,"100.00"),second=source(91,2,"200.00");
        for(CreditRepaymentLog source:List.of(first,second)){source.setCreditAccountId(null);source.setRequestKey(null);source.setRefundedAmount(null);}
        when(repaymentLogRepository.listByCustomer(100L,1L,null,null)).thenReturn(List.of(second,first));
        List<CreditRepaymentVO> result=service.listRepayments(100L,1L,8L);assertEquals(2,result.size());
        assertTrue(result.stream().allMatch(batch->batch.getAllocations().size()==1 && batch.getRefundedAmount()==null));
    }
    @Test void paidAccountWithoutHistoricalAllocationDisplaysUnknownRefundRatherThanZero(){
        CreditAccount a=account(1);a.setPaidAmount(new BigDecimal("40.00"));a.setRemainingAmount(new BigDecimal("60.00"));
        Page<CreditAccount> page=new Page<>();page.setRecords(List.of(a));when(accountRepository.findPage(100L,null,1L,1,10)).thenReturn(page);
        CreditAccountVO result=service.listAccountsPage(100L,null,1L,1,10,8L).getRecords().get(0);
        assertNull(result.getRefundedRepaymentAmount());
    }
}
