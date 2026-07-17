package com.agri_system.application.customer.service;

import com.agri_system.application.customer.dto.RechargeCommand;
import com.agri_system.application.customer.dto.RechargeResultVO;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.MarketingRuleRepository;
import com.agri_system.domain.customer.repository.VipMonthlyRecordRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 充值规则叠加算法测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RechargeStackTest {

    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private CustomerWalletRepository walletRepository;
    
    @Mock
    private WalletTransactionRepository transactionRepository;
    
    @Mock
    private MarketingRuleRepository marketingRuleRepository;
    
    @Mock
    private CouponTemplateRepository couponTemplateRepository;
    
    @Mock
    private CustomerCouponRepository couponRepository;
    
    @Mock
    private CustomerDomainService customerDomainService;
    
    @Mock
    private VipMonthlyRecordRepository vipMonthlyRecordRepository;

    @Mock
    private CustomerBenefitLogRepository benefitLogRepository;

    @InjectMocks
    private WalletRechargeService walletRechargeService;

    private Customer customer;
    private CustomerWallet balanceWallet;
    private RechargeCommand command;

    @BeforeEach
    void setUp() {
        // 初始化客户
        customer = new Customer();
        customer.setId(1L);
        customer.setName("测试客户");
        customer.setType(1); // 普通客户
        customer.setVipLevel(null);
        customer.setVipEndDate(null);

        // 初始化钱包
        balanceWallet = new CustomerWallet();
        balanceWallet.setId(1L);
        balanceWallet.setCustomerId(1L);
        balanceWallet.setWalletType(1);
        balanceWallet.setBalance(BigDecimal.ZERO);

        // 初始化充值命令
        command = new RechargeCommand();
        command.setCustomerId(1L);
        command.setAmount(new BigDecimal("1200"));
        command.setPaymentMethod(1);
        command.setCreatedBy(1L);

        // Mock基础查询
        when(customerRepository.findByIdWithLock(1L)).thenReturn(Optional.of(customer));
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 1))
                .thenReturn(Optional.of(balanceWallet));
        when(walletRepository.save(any(CustomerWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vipMonthlyRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * 场景1：精准叠加测试
     * 规则：[1000送10元], [1000送券A], [500送5元]
     * 充值 1200
     * 断言：匹配档位 1000。赠送 10元 + 券A。不送 5元 (因为 500 是低档位)
     */
    @Test
    void testPreciseStacking() {
        // 准备规则数据
        MarketingRechargeRule rule1 = new MarketingRechargeRule();
        rule1.setId(1L);
        rule1.setName("1000送10元");
        rule1.setMinRechargeAmount(new BigDecimal("1000"));
        rule1.setGiftSpecialFund(new BigDecimal("10"));
        rule1.setStatus(1);

        MarketingRechargeRule rule2 = new MarketingRechargeRule();
        rule2.setId(2L);
        rule2.setName("1000送券A");
        rule2.setMinRechargeAmount(new BigDecimal("1000"));
        rule2.setGiftTemplateId(100L);
        rule2.setGiftTemplateCount(1);
        rule2.setStatus(1);

        MarketingRechargeRule rule3 = new MarketingRechargeRule();
        rule3.setId(3L);
        rule3.setName("500送5元");
        rule3.setMinRechargeAmount(new BigDecimal("500"));
        rule3.setGiftSpecialFund(new BigDecimal("5"));
        rule3.setStatus(1);

        // Mock Repository行为
        // 第一步：查找最大门槛（应该是1000）
        when(marketingRuleRepository.findMaxMatchingThreshold(new BigDecimal("1200")))
                .thenReturn(new BigDecimal("1000"));
        
        // 第二步：获取该门槛的所有规则（只返回1000门槛的规则）
        List<MarketingRechargeRule> rulesAt1000 = new ArrayList<>();
        rulesAt1000.add(rule1);
        rulesAt1000.add(rule2);
        when(marketingRuleRepository.findRulesByThreshold(new BigDecimal("1000")))
                .thenReturn(rulesAt1000);

        // Mock优惠券模板
        CouponTemplate templateA = new CouponTemplate();
        templateA.setId(100L);
        templateA.setName("券A");
        templateA.setAmount(new BigDecimal("50"));
        templateA.setStatus(1);
        when(couponTemplateRepository.findById(100L)).thenReturn(Optional.of(templateA));

        // Mock优惠券创建
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setId(1L);
        coupon.setName("券A");
        coupon.setAmount(new BigDecimal("50"));
        when(customerDomainService.createCouponFromTemplate(any(), eq(1L))).thenReturn(coupon);
        when(couponRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock专用金钱包
        CustomerWallet specialFundWallet = new CustomerWallet();
        specialFundWallet.setId(2L);
        specialFundWallet.setCustomerId(1L);
        specialFundWallet.setWalletType(2);
        specialFundWallet.setBalance(BigDecimal.ZERO);
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 2))
                .thenReturn(Optional.of(specialFundWallet));

        // 执行充值
        RechargeResultVO result = walletRechargeService.recharge(command);

        // 断言
        assertNotNull(result);
        assertEquals(new BigDecimal("1200"), result.getRechargeAmount());
        
        // 验证专用金：应该只赠送10元（来自1000门槛的规则），不送5元（来自500门槛的规则）
        verify(walletRepository, times(1)).save(argThat(wallet -> 
            wallet.getWalletType() == 2 && 
            wallet.getBalance().compareTo(new BigDecimal("10")) == 0
        ));
        
        // 验证优惠券：应该发放1张券A
        assertEquals(1, result.getGrantedCoupons().size());
        assertEquals("券A", result.getGrantedCoupons().get(0).getName());
        
        // 验证没有调用500门槛的规则
        verify(marketingRuleRepository, never()).findRulesByThreshold(new BigDecimal("500"));
    }

    /**
     * 场景2：无规则测试
     * 充值 100
     * 断言：本金增加，无赠送
     */
    @Test
    void testNoRules() {
        // 充值金额100，低于任何门槛
        command.setAmount(new BigDecimal("100"));

        // Mock 默认返回 null（找不到匹配门槛），无需显式 stub

        // 执行充值
        RechargeResultVO result = walletRechargeService.recharge(command);

        // 断言
        assertNotNull(result);
        assertEquals(new BigDecimal("100"), result.getRechargeAmount());
        assertEquals(new BigDecimal("100"), balanceWallet.getBalance());
        
        // 验证：没有赠送专用金
        verify(walletRepository, never()).findByCustomerIdAndWalletTypeWithLock(1L, 2);
        
        // 验证：没有发放优惠券
        assertTrue(result.getGrantedCoupons().isEmpty());
        
        // 验证：没有调用获取规则的方法
        verify(marketingRuleRepository, never()).findRulesByThreshold(any());
    }

    /**
     * 场景3：同一门槛多条规则叠加测试
     * 规则：[1000送10元], [1000送20元], [1000送券A×2]
     * 充值 1500
     * 断言：匹配档位 1000。赠送 30元（10+20） + 券A×2
     */
    @Test
    void testSameThresholdStacking() {
        // 准备规则数据
        MarketingRechargeRule rule1 = new MarketingRechargeRule();
        rule1.setId(1L);
        rule1.setName("1000送10元");
        rule1.setMinRechargeAmount(new BigDecimal("1000"));
        rule1.setGiftSpecialFund(new BigDecimal("10"));
        rule1.setStatus(1);

        MarketingRechargeRule rule2 = new MarketingRechargeRule();
        rule2.setId(2L);
        rule2.setName("1000送20元");
        rule2.setMinRechargeAmount(new BigDecimal("1000"));
        rule2.setGiftSpecialFund(new BigDecimal("20"));
        rule2.setStatus(1);

        MarketingRechargeRule rule3 = new MarketingRechargeRule();
        rule3.setId(3L);
        rule3.setName("1000送券A×2");
        rule3.setMinRechargeAmount(new BigDecimal("1000"));
        rule3.setGiftTemplateId(100L);
        rule3.setGiftTemplateCount(2);
        rule3.setStatus(1);

        command.setAmount(new BigDecimal("1500"));

        // Mock Repository行为
        when(marketingRuleRepository.findMaxMatchingThreshold(new BigDecimal("1500")))
                .thenReturn(new BigDecimal("1000"));
        
        List<MarketingRechargeRule> rulesAt1000 = new ArrayList<>();
        rulesAt1000.add(rule1);
        rulesAt1000.add(rule2);
        rulesAt1000.add(rule3);
        when(marketingRuleRepository.findRulesByThreshold(new BigDecimal("1000")))
                .thenReturn(rulesAt1000);

        // Mock优惠券模板
        CouponTemplate templateA = new CouponTemplate();
        templateA.setId(100L);
        templateA.setName("券A");
        templateA.setAmount(new BigDecimal("50"));
        templateA.setStatus(1);
        when(couponTemplateRepository.findById(100L)).thenReturn(Optional.of(templateA));

        // Mock优惠券创建（应该创建2张）
        CustomerCoupon coupon1 = new CustomerCoupon();
        coupon1.setId(1L);
        coupon1.setName("券A");
        coupon1.setAmount(new BigDecimal("50"));
        CustomerCoupon coupon2 = new CustomerCoupon();
        coupon2.setId(2L);
        coupon2.setName("券A");
        coupon2.setAmount(new BigDecimal("50"));
        when(customerDomainService.createCouponFromTemplate(any(), eq(1L)))
                .thenReturn(coupon1, coupon2);
        when(couponRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock专用金钱包
        CustomerWallet specialFundWallet = new CustomerWallet();
        specialFundWallet.setId(2L);
        specialFundWallet.setCustomerId(1L);
        specialFundWallet.setWalletType(2);
        specialFundWallet.setBalance(BigDecimal.ZERO);
        when(walletRepository.findByCustomerIdAndWalletTypeWithLock(1L, 2))
                .thenReturn(Optional.of(specialFundWallet));

        // 执行充值
        RechargeResultVO result = walletRechargeService.recharge(command);

        // 断言
        assertNotNull(result);
        
        // 验证专用金：应该赠送30元（10+20）
        verify(walletRepository, times(1)).save(argThat(wallet -> 
            wallet.getWalletType() == 2 && 
            wallet.getBalance().compareTo(new BigDecimal("30")) == 0
        ));
        
        // 验证优惠券：应该发放2张券A
        assertEquals(2, result.getGrantedCoupons().size());
        assertEquals("券A", result.getGrantedCoupons().get(0).getName());
        assertEquals("券A", result.getGrantedCoupons().get(1).getName());
    }
}

