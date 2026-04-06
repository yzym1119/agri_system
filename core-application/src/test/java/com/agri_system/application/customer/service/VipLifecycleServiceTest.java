package com.agri_system.application.customer.service;

import com.agri_system.application.marketing.service.CouponService;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.*;
import com.agri_system.domain.customer.repository.*;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VIP生命周期服务测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class VipLifecycleServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private MarketingRuleRepository marketingRuleRepository;
    
    @Mock
    private CouponTemplateRepository couponTemplateRepository;
    
    @Mock
    private CustomerCouponRepository couponRepository;
    
    @Mock
    private VipMonthlyRecordRepository monthlyRecordRepository;
    
    @Mock
    private CustomerBenefitLogRepository benefitLogRepository;
    
    @Mock
    private CustomerDomainService customerDomainService;
    
    @Mock
    private CustomerWalletRepository walletRepository;
    
    @Mock
    private WalletTransactionRepository transactionRepository;
    
    @Mock
    private CouponService couponService;

    @InjectMocks
    private VipLifecycleService vipLifecycleService;

    private Customer vipCustomer;
    private Customer normalCustomer;
    private Customer expiredVipCustomer;
    private CouponTemplate couponTemplate;
    private MarketingVipRights couponRight;
    private MarketingVipRights specialFundRight;
    private CustomerWallet specialFundWallet;

    @BeforeEach
    void setUp() {
        // 初始化有效VIP客户
        vipCustomer = new Customer();
        vipCustomer.setId(1L);
        vipCustomer.setName("VIP客户");
        vipCustomer.setType(2); // VIP
        vipCustomer.setVipLevel(1);
        vipCustomer.setVipEndDate(LocalDate.now().plusDays(30));

        // 初始化普通客户
        normalCustomer = new Customer();
        normalCustomer.setId(2L);
        normalCustomer.setName("普通客户");
        normalCustomer.setType(1); // 普通
        normalCustomer.setVipLevel(null);

        // 初始化过期VIP客户
        expiredVipCustomer = new Customer();
        expiredVipCustomer.setId(3L);
        expiredVipCustomer.setName("过期VIP");
        expiredVipCustomer.setType(2); // VIP
        expiredVipCustomer.setVipLevel(1);
        expiredVipCustomer.setVipEndDate(LocalDate.now().minusDays(1));

        // 初始化优惠券模板
        couponTemplate = new CouponTemplate();
        couponTemplate.setId(1L);
        couponTemplate.setName("测试优惠券");
        couponTemplate.setStatus(1); // 启用
        couponTemplate.setTotalCount(-1); // 不限

        // 初始化送券权益
        couponRight = new MarketingVipRights();
        couponRight.setId(1L);
        couponRight.setVipLevel(1);
        couponRight.setBenefitType(1); // 送券
        couponRight.setTemplateId(1L);
        couponRight.setTemplateCount(2);
        couponRight.setStatus(1);

        // 初始化送专用金权益
        specialFundRight = new MarketingVipRights();
        specialFundRight.setId(2L);
        specialFundRight.setVipLevel(1);
        specialFundRight.setBenefitType(2); // 送专用金
        specialFundRight.setBenefitValue(new BigDecimal("100.00"));
        specialFundRight.setStatus(1);

        // 初始化专用金钱包
        specialFundWallet = new CustomerWallet();
        specialFundWallet.setId(1L);
        specialFundWallet.setCustomerId(1L);
        specialFundWallet.setWalletType(2); // 专用金
        specialFundWallet.setBalance(BigDecimal.ZERO);
        specialFundWallet.setFrozenBalance(BigDecimal.ZERO);
        specialFundWallet.setVersion(0);
    }

    /**
     * 测试用例1：正常发放VIP月度权益（送券+送专用金）
     */
    @Test
    void testGrantMonthlyBenefit_Success() {
        // Given
        String currentMonth = "2025-01";
        List<MarketingVipRights> rights = List.of(couponRight, specialFundRight);

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(1L, currentMonth))
                .thenReturn(Optional.empty());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(vipCustomer));
        when(marketingRuleRepository.findVipRightsByLevel(1)).thenReturn(rights);
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(couponTemplate));
        doNothing().when(couponService).validateGrant(anyLong(), anyLong(), anyInt());
        when(customerDomainService.createCouponFromTemplate(any(), anyLong()))
                .thenReturn(new CustomerCoupon());
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 2))
                .thenReturn(Optional.of(specialFundWallet));

        // When
        vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);

        // Then
        // 验证：发放了2张优惠券
        verify(couponRepository, times(2)).save(any(CustomerCoupon.class));
        
        // 验证：专用金余额增加
        ArgumentCaptor<CustomerWallet> walletCaptor = ArgumentCaptor.forClass(CustomerWallet.class);
        verify(walletRepository, atLeastOnce()).save(walletCaptor.capture());
        CustomerWallet savedWallet = walletCaptor.getValue();
        assertEquals(new BigDecimal("100.00"), savedWallet.getBalance());

        // 验证：记录了流水
        ArgumentCaptor<WalletTransaction> transactionCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        WalletTransaction transaction = transactionCaptor.getValue();
        assertEquals(new BigDecimal("100.00"), transaction.getAmount());
        assertEquals("VIP每月权益：赠送专用金", transaction.getRemark());

        // 验证：记录了月度权益记录
        ArgumentCaptor<VipMonthlyRecord> recordCaptor = ArgumentCaptor.forClass(VipMonthlyRecord.class);
        verify(monthlyRecordRepository).save(recordCaptor.capture());
        VipMonthlyRecord record = recordCaptor.getValue();
        assertEquals(1L, record.getCustomerId());
        assertEquals(currentMonth, record.getYearMonth());
        assertEquals(1, record.getVipLevel());

        // 验证：记录了权益日志
        ArgumentCaptor<CustomerBenefitLog> logCaptor = ArgumentCaptor.forClass(CustomerBenefitLog.class);
        verify(benefitLogRepository, atLeastOnce()).save(logCaptor.capture());
    }

    /**
     * 测试用例2：幂等性测试 - 已发放过则跳过
     */
    @Test
    void testGrantMonthlyBenefit_Idempotent() {
        // Given
        String currentMonth = "2025-01";
        VipMonthlyRecord existingRecord = new VipMonthlyRecord();
        existingRecord.setCustomerId(1L);
        existingRecord.setYearMonth(currentMonth);

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(1L, currentMonth))
                .thenReturn(Optional.of(existingRecord));

        // When
        vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);

        // Then
        // 验证：没有发放优惠券
        verify(couponRepository, never()).save(any());
        // 验证：没有更新钱包
        verify(walletRepository, never()).save(any());
        // 验证：没有查询客户
        verify(customerRepository, never()).findById(anyLong());
    }

    /**
     * 测试用例3：VIP过期跳过
     */
    @Test
    void testGrantMonthlyBenefit_ExpiredVip() {
        // Given
        String currentMonth = "2025-01";

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(3L, currentMonth))
                .thenReturn(Optional.empty());
        when(customerRepository.findById(3L)).thenReturn(Optional.of(expiredVipCustomer));

        // When
        vipLifecycleService.grantMonthlyBenefit(3L, currentMonth);

        // Then
        // 验证：没有发放权益
        verify(couponRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        // 验证：没有记录月度权益
        verify(monthlyRecordRepository, never()).save(any());
    }

    /**
     * 测试用例4：普通客户跳过
     */
    @Test
    void testGrantMonthlyBenefit_NormalCustomer() {
        // Given
        String currentMonth = "2025-01";

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(2L, currentMonth))
                .thenReturn(Optional.empty());
        when(customerRepository.findById(2L)).thenReturn(Optional.of(normalCustomer));

        // When
        vipLifecycleService.grantMonthlyBenefit(2L, currentMonth);

        // Then
        // 验证：没有发放权益
        verify(couponRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    /**
     * 测试用例5：优惠券模板停用容错
     */
    @Test
    void testGrantMonthlyBenefit_CouponDisabled() {
        // Given
        String currentMonth = "2025-01";
        CouponTemplate disabledTemplate = new CouponTemplate();
        disabledTemplate.setId(1L);
        disabledTemplate.setName("停用优惠券");
        disabledTemplate.setStatus(0); // 停用

        List<MarketingVipRights> rights = List.of(couponRight, specialFundRight);

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(1L, currentMonth))
                .thenReturn(Optional.empty());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(vipCustomer));
        when(marketingRuleRepository.findVipRightsByLevel(1)).thenReturn(rights);
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(disabledTemplate));
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 2))
                .thenReturn(Optional.of(specialFundWallet));

        // When
        vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);

        // Then
        // 验证：没有发放优惠券（因为模板停用）
        verify(couponRepository, never()).save(any());
        // 验证：专用金正常发放
        verify(walletRepository, atLeastOnce()).save(any());
        // 验证：记录了失败日志
        ArgumentCaptor<CustomerBenefitLog> logCaptor = ArgumentCaptor.forClass(CustomerBenefitLog.class);
        verify(benefitLogRepository, atLeastOnce()).save(logCaptor.capture());
    }

    /**
     * 测试用例6：优惠券发放失败容错（coupon-5001）
     */
    @Test
    void testGrantMonthlyBenefit_CouponGrantFailed() {
        // Given
        String currentMonth = "2025-01";
        List<MarketingVipRights> rights = List.of(couponRight, specialFundRight);

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(1L, currentMonth))
                .thenReturn(Optional.empty());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(vipCustomer));
        when(marketingRuleRepository.findVipRightsByLevel(1)).thenReturn(rights);
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(couponTemplate));
        doThrow(new IllegalArgumentException("coupon-5001: 该优惠券模板已停用"))
                .when(couponService).validateGrant(anyLong(), anyLong(), anyInt());
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 2))
                .thenReturn(Optional.of(specialFundWallet));

        // When
        vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);

        // Then
        // 验证：没有发放优惠券
        verify(couponRepository, never()).save(any());
        // 验证：专用金正常发放
        verify(walletRepository, atLeastOnce()).save(any());
        // 验证：记录了失败日志
        verify(benefitLogRepository, atLeastOnce()).save(any(CustomerBenefitLog.class));
    }

    /**
     * 测试用例7：专用金钱包不存在时自动创建
     */
    @Test
    void testGrantMonthlyBenefit_AutoCreateSpecialFundWallet() {
        // Given
        String currentMonth = "2025-01";
        List<MarketingVipRights> rights = List.of(specialFundRight);

        when(monthlyRecordRepository.findByCustomerIdAndYearMonth(1L, currentMonth))
                .thenReturn(Optional.empty());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(vipCustomer));
        when(marketingRuleRepository.findVipRightsByLevel(1)).thenReturn(rights);
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 2))
                .thenReturn(Optional.empty());
        when(walletRepository.save(any(CustomerWallet.class))).thenAnswer(invocation -> {
            CustomerWallet wallet = invocation.getArgument(0);
            wallet.setId(1L);
            return wallet;
        });

        // When
        vipLifecycleService.grantMonthlyBenefit(1L, currentMonth);

        // Then
        // 验证：创建了专用金钱包
        ArgumentCaptor<CustomerWallet> walletCaptor = ArgumentCaptor.forClass(CustomerWallet.class);
        verify(walletRepository, atLeast(2)).save(walletCaptor.capture());
        // 第一次保存是创建钱包，第二次是更新余额
        CustomerWallet createdWallet = walletCaptor.getAllValues().get(0);
        assertEquals(1L, createdWallet.getCustomerId());
        assertEquals(2, createdWallet.getWalletType());
    }

    /**
     * 测试用例8：VIP降级 - 正常降级
     */
    @Test
    void testDowngradeExpiredVips_Success() {
        // Given
        List<Customer> vipCustomers = List.of(expiredVipCustomer, vipCustomer);

        when(customerRepository.findAllVipCustomers()).thenReturn(vipCustomers);

        // When
        vipLifecycleService.downgradeExpiredVips();

        // Then
        // 验证：过期VIP被降级
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertEquals(1, savedCustomer.getType()); // 普通客户
        assertNull(savedCustomer.getVipLevel());
        assertNull(savedCustomer.getVipEndDate());

        // 验证：未过期VIP没有被降级
        verify(customerRepository, times(1)).save(any());
    }

    /**
     * 测试用例9：VIP降级 - 没有过期VIP
     */
    @Test
    void testDowngradeExpiredVips_NoExpiredVip() {
        // Given
        List<Customer> vipCustomers = List.of(vipCustomer);

        when(customerRepository.findAllVipCustomers()).thenReturn(vipCustomers);

        // When
        vipLifecycleService.downgradeExpiredVips();

        // Then
        // 验证：没有客户被降级
        verify(customerRepository, never()).save(any());
    }

    /**
     * 测试用例10：获取当前月份
     */
    @Test
    void testGetCurrentMonth() {
        // When
        String currentMonth = vipLifecycleService.getCurrentMonth();

        // Then
        assertNotNull(currentMonth);
        assertTrue(currentMonth.matches("\\d{4}-\\d{2}")); // 格式：yyyy-MM
    }
}

