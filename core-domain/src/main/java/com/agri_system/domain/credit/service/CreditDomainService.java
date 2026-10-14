package com.agri_system.domain.credit.service;

import com.agri_system.domain.credit.model.CreditAccount;
import com.agri_system.domain.credit.repository.CreditAccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CreditDomainService {

    private final CreditAccountRepository accountRepository;

    public CreditDomainService(CreditAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public BigDecimal calculateTotalDebt(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("客户ID不能为空");
        }
        BigDecimal sum = accountRepository.sumRemainingAmount(customerId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public void addDebt(Long customerId, Long orderId, BigDecimal amount, Long storeId) {
        if (customerId == null || orderId == null || storeId == null || amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("赊销必须绑定原订单、客户、门店及精确到分的正金额");
        }
        // 幂等性校验：防止重复记账
        accountRepository.findByOrderId(orderId).ifPresent(existing -> {
            throw new IllegalStateException("该订单已记为欠款，请勿重复操作");
        });

        CreditAccount account = new CreditAccount();
        account.setCustomerId(customerId);
        account.setOrderId(orderId);
        account.setStoreId(storeId);
        account.setCreditAmount(amount);
        account.setPaidAmount(BigDecimal.ZERO);
        account.setRemainingAmount(amount);
        account.setDueDate(LocalDate.now().plusDays(30)); // 默认30天账期
        account.setStatus(1); // 1-未结清
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now()); // 修复：设置updated_at
        accountRepository.save(account);
    }
}






