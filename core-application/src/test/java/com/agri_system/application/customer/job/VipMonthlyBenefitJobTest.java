package com.agri_system.application.customer.job;

import com.agri_system.application.customer.service.VipLifecycleService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VIP月度权益发放定时任务测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class VipMonthlyBenefitJobTest {

    @Mock
    private VipLifecycleService vipLifecycleService;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private VipMonthlyBenefitJob vipMonthlyBenefitJob;

    private Customer vipCustomer1;
    private Customer vipCustomer2;
    private Customer expiredVipCustomer;
    private Customer normalCustomer;

    @BeforeEach
    void setUp() {
        // 初始化有效VIP客户1
        vipCustomer1 = new Customer();
        vipCustomer1.setId(1L);
        vipCustomer1.setName("VIP客户1");
        vipCustomer1.setType(2); // VIP
        vipCustomer1.setVipLevel(1);
        vipCustomer1.setVipEndDate(LocalDate.now().plusDays(30));

        // 初始化有效VIP客户2
        vipCustomer2 = new Customer();
        vipCustomer2.setId(2L);
        vipCustomer2.setName("VIP客户2");
        vipCustomer2.setType(2); // VIP
        vipCustomer2.setVipLevel(2);
        vipCustomer2.setVipEndDate(LocalDate.now().plusDays(60));

        // 初始化过期VIP客户
        expiredVipCustomer = new Customer();
        expiredVipCustomer.setId(3L);
        expiredVipCustomer.setName("过期VIP");
        expiredVipCustomer.setType(2); // VIP
        expiredVipCustomer.setVipLevel(1);
        expiredVipCustomer.setVipEndDate(LocalDate.now().minusDays(1));

        // 初始化普通客户
        normalCustomer = new Customer();
        normalCustomer.setId(4L);
        normalCustomer.setName("普通客户");
        normalCustomer.setType(1); // 普通
        normalCustomer.setVipLevel(null);
    }

    /**
     * 测试用例1：正常执行 - 为所有有效VIP客户发放权益
     */
    @Test
    void testExecuteMonthlyBenefit_Success() {
        // Given
        List<Customer> vipCustomers = List.of(vipCustomer1, vipCustomer2, expiredVipCustomer);

        when(customerRepository.findAllVipCustomers()).thenReturn(vipCustomers);
        when(vipLifecycleService.getCurrentMonth()).thenReturn("2025-01");
        doNothing().when(vipLifecycleService).grantMonthlyBenefit(anyLong(), anyString());

        // When
        vipMonthlyBenefitJob.executeMonthlyBenefit();

        // Then
        // 验证：为有效VIP客户发放权益（跳过过期VIP）
        verify(vipLifecycleService, times(2)).grantMonthlyBenefit(anyLong(), eq("2025-01"));
        verify(vipLifecycleService).grantMonthlyBenefit(1L, "2025-01");
        verify(vipLifecycleService).grantMonthlyBenefit(2L, "2025-01");
        // 验证：没有为过期VIP发放
        verify(vipLifecycleService, never()).grantMonthlyBenefit(eq(3L), anyString());
    }

    /**
     * 测试用例2：单个客户发放失败不影响其他客户
     */
    @Test
    void testExecuteMonthlyBenefit_SingleFailure() {
        // Given
        List<Customer> vipCustomers = List.of(vipCustomer1, vipCustomer2);

        when(customerRepository.findAllVipCustomers()).thenReturn(vipCustomers);
        when(vipLifecycleService.getCurrentMonth()).thenReturn("2025-01");
        doThrow(new RuntimeException("发放失败")).when(vipLifecycleService)
                .grantMonthlyBenefit(1L, "2025-01");
        doNothing().when(vipLifecycleService).grantMonthlyBenefit(2L, "2025-01");

        // When
        assertDoesNotThrow(() -> vipMonthlyBenefitJob.executeMonthlyBenefit());

        // Then
        // 验证：两个客户都尝试发放
        verify(vipLifecycleService, times(2)).grantMonthlyBenefit(anyLong(), anyString());
        // 验证：第二个客户仍然被处理
        verify(vipLifecycleService).grantMonthlyBenefit(2L, "2025-01");
    }

    /**
     * 测试用例3：没有VIP客户
     */
    @Test
    void testExecuteMonthlyBenefit_NoVipCustomers() {
        // Given
        List<Customer> vipCustomers = new ArrayList<>();

        when(customerRepository.findAllVipCustomers()).thenReturn(vipCustomers);
        when(vipLifecycleService.getCurrentMonth()).thenReturn("2025-01");

        // When
        vipMonthlyBenefitJob.executeMonthlyBenefit();

        // Then
        // 验证：没有调用发放方法
        verify(vipLifecycleService, never()).grantMonthlyBenefit(anyLong(), anyString());
    }

    /**
     * 测试用例4：所有VIP都已过期
     */
    @Test
    void testExecuteMonthlyBenefit_AllExpired() {
        // Given
        List<Customer> vipCustomers = List.of(expiredVipCustomer);

        when(customerRepository.findAllVipCustomers()).thenReturn(vipCustomers);
        when(vipLifecycleService.getCurrentMonth()).thenReturn("2025-01");

        // When
        vipMonthlyBenefitJob.executeMonthlyBenefit();

        // Then
        // 验证：没有调用发放方法
        verify(vipLifecycleService, never()).grantMonthlyBenefit(anyLong(), anyString());
    }

    /**
     * 测试用例5：查询VIP客户失败
     */
    @Test
    void testExecuteMonthlyBenefit_QueryFailed() {
        // Given
        when(customerRepository.findAllVipCustomers())
                .thenThrow(new RuntimeException("数据库连接失败"));

        // When & Then
        assertThrows(RuntimeException.class, () -> vipMonthlyBenefitJob.executeMonthlyBenefit());
    }
}









