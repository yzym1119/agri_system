package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.CommissionRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CommissionRuleRepository {

    CommissionRule save(CommissionRule rule);

    Optional<CommissionRule> findById(Long id);

    List<CommissionRule> findByCategory(Integer category);

    Optional<CommissionRule> findBestSalesRule(Long productId, Long packageId, Long staffId);

    List<CommissionRule> findRechargeRules(BigDecimal amount);

    List<CommissionRule> query(Integer category, Long targetId, Long staffId, Integer enabled);
}

