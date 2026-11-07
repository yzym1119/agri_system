package com.agri_system.application.customer.service;

import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动发专用金：钱包余额与权益台账必须在同一事务提交或回滚，
 * 避免「余额已增加、台账缺失」。金额防线仍在 WalletApplicationService.addSpecialFund。
 */
@Service
@RequiredArgsConstructor
public class SpecialFundGrantService {
    private final WalletApplicationService walletService;
    private final CustomerBenefitLogRepository benefitLogRepository;

    @Transactional(rollbackFor = Exception.class)
    public BigDecimal grant(Long customerId, BigDecimal amount, Long createdBy, String remark) {
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
        return balance;
    }
}
