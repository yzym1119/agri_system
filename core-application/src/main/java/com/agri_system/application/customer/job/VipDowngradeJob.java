package com.agri_system.application.customer.job;

import com.agri_system.application.customer.service.VipLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * VIP降级定时任务
 * 每天凌晨2点执行，检查并降级过期的VIP客户
 * 
 * @author AI Generated
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipDowngradeJob {
    
    private final VipLifecycleService vipLifecycleService;
    
    /**
     * 执行VIP降级任务
     * Cron表达式：每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void executeDowngrade() {
        log.info("开始执行VIP降级任务");
        
        try {
            vipLifecycleService.downgradeExpiredVips();
            log.info("VIP降级任务完成");
        } catch (Exception e) {
            log.error("VIP降级任务执行失败", e);
            throw e; // 重新抛出异常，触发事务回滚（如果需要）
        }
    }
}









