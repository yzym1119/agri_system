package com.agri_system.application.customer.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.SpecialFundGrantRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动发专用金：钱包余额与权益台账必须在同一事务提交或回滚，
 * 避免「余额已增加、台账缺失」。金额防线仍在 WalletApplicationService.addSpecialFund。
 *
 * <p>幂等：request_key 为主键占位（复用 V133 持久请求模式）。同一 request_key
 * 的重复请求在 COMPLETED 后直接重放已记录结果，不再二次发放。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecialFundGrantService {
    private final WalletApplicationService walletService;
    private final CustomerBenefitLogRepository benefitLogRepository;
    private final SpecialFundGrantRequestRepository grantRequestRepository;

    @Transactional(rollbackFor = Exception.class)
    public BigDecimal grant(Long customerId, BigDecimal amount, Long createdBy, String remark, String requestKey) {
        if (!StringUtils.hasText(requestKey) || requestKey.length() > 64) {
            throw new IllegalArgumentException("发放请求标识不能为空且不超过64字符");
        }
        grantRequestRepository.reserve(requestKey, customerId, amount);
        var existing = grantRequestRepository.find(requestKey, true)
                .orElseThrow(() -> new IllegalStateException("专用金发放请求占位失败"));
        if (existing.completed()) {
            // 幂等重放：请求已成功发放过，返回已记录的发放后余额，不二次加钱。
            return existing.balanceAfter();
        }
        BigDecimal balance = walletService.addSpecialFund(customerId, amount,
                "MANUAL_GRANT", null, createdBy, remark);
        CustomerBenefitLog log = new CustomerBenefitLog();
        log.setCustomerId(customerId);
        log.setSourceType(3); // 3-手动发放
        log.setBenefitContent("专用金¥" + amount);
        log.setAssociatedId(null);
        log.setCreatedBy(createdBy);
        log.setCreatedAt(LocalDateTime.now());
        benefitLogRepository.save(log);
        grantRequestRepository.complete(requestKey, balance);
        return balance;
    }
}
