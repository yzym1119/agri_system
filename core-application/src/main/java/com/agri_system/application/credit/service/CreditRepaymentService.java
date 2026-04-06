package com.agri_system.application.credit.service;

import com.agri_system.application.credit.dto.CreditRepayCommand;
import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.model.CreditRepaymentLog;
import com.agri_system.domain.credit.repository.CreditAccountRepository;
import com.agri_system.domain.credit.repository.CreditRepaymentLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditRepaymentService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditRepaymentLogRepository repaymentLogRepository;

    @Transactional(rollbackFor = Exception.class)
    public CreditRepaymentLog repay(CreditRepayCommand command) {
        if (command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("还款金额必须大于0");
        }
        BigDecimal outstanding = creditAccountRepository.sumRemainingAmount(command.getCustomerId());
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("该客户无欠款");
        }
        if (command.getAmount().compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("还款金额超过剩余欠款");
        }

        CreditRepaymentLog log = new CreditRepaymentLog();
        log.setRepaymentNo(generateRepaymentNo());
        log.setCustomerId(command.getCustomerId());
        log.setStoreId(command.getStoreId());
        log.setAmount(command.getAmount());
        log.setPaymentMethod(command.getPaymentMethod());
        log.setTransactionId(command.getTransactionId());
        log.setRemark(command.getRemark());
        log.setCreatedBy(command.getCreatedBy());
        log.setCreatedAt(LocalDateTime.now());
        repaymentLogRepository.save(log);

        BigDecimal remaining = command.getAmount();
        List<CreditAccount> accounts = creditAccountRepository.findOutstandingAccounts(command.getCustomerId());
        for (CreditAccount account : accounts) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal paid = account.applyRepayment(remaining);
            remaining = remaining.subtract(paid);
            creditAccountRepository.save(account);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("还款金额未全部核销，请检查台账数据");
        }

        return log;
    }

    private String generateRepaymentNo() {
        return "CRP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}






