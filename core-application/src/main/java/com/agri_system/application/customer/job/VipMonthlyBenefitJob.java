package com.agri_system.application.customer.job;

import com.agri_system.application.customer.service.VipLifecycleService;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * VIP月度权益发放定时任务
 * 每月1日00:00:00执行，为所有有效VIP客户发放月度权益
 * 
 * @author AI Generated
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipMonthlyBenefitJob {
    
    private final VipLifecycleService vipLifecycleService;
    private final CustomerRepository customerRepository;
    
    /**
     * 执行VIP月度权益发放
     * Cron表达式：每月1日00:00:00执行
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    @Transactional(rollbackFor = Exception.class)
    public void executeMonthlyBenefit() {
        log.info("开始执行VIP月度权益发放任务");
        
        try {
            // 获取当前月份（格式：2025-01）
            String currentMonth = vipLifecycleService.getCurrentMonth();
            
            // 查询所有VIP客户
            List<Customer> vipCustomers = customerRepository.findAllVipCustomers();
            log.info("查询到 {} 个VIP客户", vipCustomers.size());
            
            int successCount = 0;
            int skipCount = 0;
            int errorCount = 0;
            
            for (Customer customer : vipCustomers) {
                try {
                    // 检查VIP是否过期
                    if (customer.isVipExpired()) {
                        log.debug("客户 {} VIP已过期，跳过", customer.getId());
                        skipCount++;
                        continue;
                    }
                    
                    // 发放月度权益（方法内部有幂等性检查）
                    vipLifecycleService.grantMonthlyBenefit(customer.getId(), currentMonth);
                    successCount++;
                    
                } catch (Exception e) {
                    log.error("客户 {} 月度权益发放失败: {}", customer.getId(), e.getMessage(), e);
                    errorCount++;
                    // 继续处理下一个客户，不中断整个任务
                }
            }
            
            log.info("VIP月度权益发放任务完成，成功: {}, 跳过: {}, 失败: {}", 
                    successCount, skipCount, errorCount);
                    
        } catch (Exception e) {
            log.error("VIP月度权益发放任务执行失败", e);
            throw e; // 重新抛出异常，触发事务回滚（如果需要）
        }
    }
}









