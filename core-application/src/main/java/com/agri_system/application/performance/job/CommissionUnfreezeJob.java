package com.agri_system.application.performance.job;

import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 提成日解冻任务:每天 02:05 把到期(status=1 且 freeze_until<=now)的冻结提成置为已解冻。
 * 早于月初 04:00 结算,保证结算时本月应解冻的均已解冻。
 * 天然幂等(status=1 过滤);漏跑只会延迟发放,绝不超发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommissionUnfreezeJob {

    private final StaffPerformanceLogRepository logRepository;

    @Scheduled(cron = "0 5 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeDue() {
        int n = logRepository.unfreezeDue(LocalDateTime.now());
        log.info("Commission unfreeze: {} logs", n);
    }
}
