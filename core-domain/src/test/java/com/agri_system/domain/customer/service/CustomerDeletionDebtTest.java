package com.agri_system.domain.customer.service;

import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.marketing.points.repository.PointsExchangeOrderRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDeletionDebtTest {
    @Test void anyOutstandingDebtBlocksDeletionEvenWhenStatusIsOverdueOrUnknown(){
        var credits=mock(CreditAccountRepository.class);var service=new CustomerDomainService(mock(CustomerWalletRepository.class),credits,mock(OrderRepository.class),mock(AfterSalesRepository.class),mock(PointsExchangeOrderRepository.class));
        for(Integer status:new Integer[]{1,3,2,null}){
            var account=new CreditAccount();account.setRemainingAmount(new BigDecimal("20.00"));account.setStatus(status);when(credits.findOutstandingAccounts(7L)).thenReturn(List.of(account));
            assertThrows(IllegalArgumentException.class,()->service.validateDelete(7L),"剩余欠款不因状态不同而消失");
        }
    }
}
