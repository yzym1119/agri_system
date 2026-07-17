package com.agri_system.application.customer.service;

import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.model.MarketingVipRights;
import com.agri_system.domain.customer.model.VipMonthlyRecord;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.repository.MarketingRuleRepository;
import com.agri_system.domain.customer.repository.VipMonthlyRecordRepository;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VIP生命周期服务集成测试
 * 使用真实数据库进行测试
 * 
 * 前提条件：
 * 1. 数据库中已有VIP客户（type=2, vipLevel=1-5）
 * 2. 数据库中已有优惠券模板（status=1）
 * 3. 数据库中已有VIP月度权益配置（marketing_vip_rights表）
 * 
 * @author AI Generated
 */
@SpringBootTest(classes = com.agri_system.api.admin.AdminApplication.class)
@ActiveProfiles("test") // 使用测试环境配置
@Disabled("集成测试：需独立的 agri_system_test 测试库及种子数据，本地环境未配置")
@Transactional // 测试后回滚，不影响数据库
class VipLifecycleServiceIntegrationTest {

    @Autowired
    private VipLifecycleService vipLifecycleService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VipMonthlyRecordRepository monthlyRecordRepository;

    @Autowired
    private CustomerCouponRepository couponRepository;

    @Autowired
    private CustomerWalletRepository walletRepository;

    @Autowired
    private CustomerBenefitLogRepository benefitLogRepository;

    @Autowired
    private MarketingRuleRepository marketingRuleRepository;

    private Customer testVipCustomer;
    private String currentMonth;

    @BeforeEach
    void setUp() {
        // 获取当前月份
        currentMonth = vipLifecycleService.getCurrentMonth();

        // 查找一个有效的VIP客户（如果数据库中有的话）
        // 如果没有，测试会跳过
        List<Customer> vipCustomers = customerRepository.findAllVipCustomers();
        if (!vipCustomers.isEmpty()) {
            testVipCustomer = vipCustomers.stream()
                    .filter(c -> !c.isVipExpired())
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * 集成测试1：使用真实数据库测试VIP月度权益发放
     * 
     * 前提：数据库中需要有：
     * - VIP客户（type=2, vipLevel=1, vipEndDate > today）
     * - VIP权益配置（marketing_vip_rights表，vip_level=1, status=1）
     * - 优惠券模板（如果权益配置中有送券）
     */
    @Test
    void testGrantMonthlyBenefit_Integration() {
        // 如果没有有效的VIP客户，跳过测试
        if (testVipCustomer == null) {
            System.out.println("跳过测试：数据库中没有有效的VIP客户");
            return;
        }

        // Given: 确保该客户本月未发放过权益
        Long customerId = testVipCustomer.getId();
        Optional<VipMonthlyRecord> existingRecord = monthlyRecordRepository
                .findByCustomerIdAndYearMonth(customerId, currentMonth);

        if (existingRecord.isPresent()) {
            System.out.println("跳过测试：客户 " + customerId + " 本月已发放过权益");
            return;
        }

        // 记录发放前的状态
        com.mybatisflex.core.paginate.Page<CustomerCoupon> couponPageBefore = 
                couponRepository.findPage(1, 1000, customerId, null);
        long couponCountBefore = couponPageBefore.getTotalRow();
        
        CustomerWallet specialFundWalletBefore = walletRepository
                .findByCustomerIdAndWalletType(customerId, 2)
                .orElse(null);
        BigDecimal specialFundBefore = specialFundWalletBefore != null 
                ? specialFundWalletBefore.getBalance() 
                : BigDecimal.ZERO;

        // When: 发放月度权益
        vipLifecycleService.grantMonthlyBenefit(customerId, currentMonth);

        // Then: 验证月度权益记录
        Optional<VipMonthlyRecord> record = monthlyRecordRepository
                .findByCustomerIdAndYearMonth(customerId, currentMonth);
        assertTrue(record.isPresent(), "应该创建月度权益记录");
        assertEquals(customerId, record.get().getCustomerId());
        assertEquals(currentMonth, record.get().getYearMonth());

        // 验证：权益日志记录
        com.mybatisflex.core.paginate.Page<CustomerBenefitLog> logPage = 
                benefitLogRepository.findPage(1, 1000, customerId);
        assertTrue(logPage.getTotalRow() > 0, "应该有权益日志记录");

        // 验证：如果配置了送券，应该有优惠券发放
        List<MarketingVipRights> rights = marketingRuleRepository
                .findVipRightsByLevel(testVipCustomer.getVipLevel());
        boolean hasCouponRight = rights.stream()
                .anyMatch(r -> r.isCouponBenefit() && r.getTemplateId() != null);
        
        if (hasCouponRight) {
            com.mybatisflex.core.paginate.Page<CustomerCoupon> couponPageAfter = 
                    couponRepository.findPage(1, 1000, customerId, null);
            long couponCountAfter = couponPageAfter.getTotalRow();
            assertTrue(couponCountAfter > couponCountBefore, "应该发放了优惠券");
        }

        // 验证：如果配置了送专用金，应该有专用金增加
        boolean hasSpecialFundRight = rights.stream()
                .anyMatch(r -> r.isSpecialFundBenefit() 
                        && r.getBenefitValue() != null 
                        && r.getBenefitValue().compareTo(BigDecimal.ZERO) > 0);

        if (hasSpecialFundRight) {
            CustomerWallet specialFundWalletAfter = walletRepository
                    .findByCustomerIdAndWalletType(customerId, 2)
                    .orElse(null);
            assertNotNull(specialFundWalletAfter, "应该有专用金钱包");
            assertTrue(specialFundWalletAfter.getBalance().compareTo(specialFundBefore) > 0,
                    "专用金余额应该增加");
        }
    }

    /**
     * 集成测试2：幂等性测试
     */
    @Test
    void testGrantMonthlyBenefit_Idempotent_Integration() {
        if (testVipCustomer == null) {
            System.out.println("跳过测试：数据库中没有有效的VIP客户");
            return;
        }

        Long customerId = testVipCustomer.getId();

        // 第一次发放
        vipLifecycleService.grantMonthlyBenefit(customerId, currentMonth);

        // 记录第一次发放后的状态
        com.mybatisflex.core.paginate.Page<CustomerCoupon> couponPageAfterFirst = 
                couponRepository.findPage(1, 1000, customerId, null);
        long couponCountAfterFirst = couponPageAfterFirst.getTotalRow();
        
        CustomerWallet walletAfterFirst = walletRepository
                .findByCustomerIdAndWalletType(customerId, 2)
                .orElse(null);
        BigDecimal specialFundAfterFirst = walletAfterFirst != null 
                ? walletAfterFirst.getBalance() 
                : BigDecimal.ZERO;

        // 第二次发放（应该跳过）
        vipLifecycleService.grantMonthlyBenefit(customerId, currentMonth);

        // 验证：状态没有变化
        com.mybatisflex.core.paginate.Page<CustomerCoupon> couponPageAfterSecond = 
                couponRepository.findPage(1, 1000, customerId, null);
        long couponCountAfterSecond = couponPageAfterSecond.getTotalRow();
        assertEquals(couponCountAfterFirst, couponCountAfterSecond, 
                "第二次发放应该跳过，优惠券数量不应增加");

        CustomerWallet walletAfterSecond = walletRepository
                .findByCustomerIdAndWalletType(customerId, 2)
                .orElse(null);
        BigDecimal specialFundAfterSecond = walletAfterSecond != null 
                ? walletAfterSecond.getBalance() 
                : BigDecimal.ZERO;
        assertEquals(specialFundAfterFirst, specialFundAfterSecond,
                "第二次发放应该跳过，专用金余额不应增加");
    }

    /**
     * 集成测试3：VIP降级测试
     */
    @Test
    void testDowngradeExpiredVips_Integration() {
        // 查找所有VIP客户
        List<Customer> vipCustomers = customerRepository.findAllVipCustomers();
        
        if (vipCustomers.isEmpty()) {
            System.out.println("跳过测试：数据库中没有VIP客户");
            return;
        }

        // When: 执行降级任务
        vipLifecycleService.downgradeExpiredVips();

        // Then: 验证过期VIP已被降级
        List<Customer> allCustomers = customerRepository.findAllVipCustomers();
        long expiredCountAfter = allCustomers.stream()
                .filter(Customer::isVipExpired)
                .count();

        assertEquals(0, expiredCountAfter, "所有过期VIP应该已被降级");
    }
}

