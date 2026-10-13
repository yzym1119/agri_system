package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.repository.CommissionRuleRepository;
import com.agri_system.infrastructure.performance.mapper.CommissionRuleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommissionRuleRepositoryImpl implements CommissionRuleRepository {

    private final CommissionRuleMapper mapper;

    @Override
    public CommissionRule save(CommissionRule rule) {
        if (rule.getId() == null) {
            mapper.insert(rule);
        } else {
            mapper.update(rule);
        }
        return rule;
    }

    @Override
    public Optional<CommissionRule> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public List<CommissionRule> findByCategory(Integer category) {
        QueryWrapper query = QueryWrapper.create()
                .where("rule_category = ?", category)
                .and("enabled = 1");
        return mapper.selectListByQuery(query);
    }

    @Override
    public Optional<CommissionRule> findBestSalesRule(Long productId, Long packageId, Long staffId) {
        QueryWrapper query = QueryWrapper.create()
                .where("enabled = 1");
        if (packageId != null && productId != null) {
            query.and("((rule_category = ? AND (target_id IS NULL OR target_id = ?))"
                            + " OR (rule_category = ? AND (target_id IS NULL OR target_id = ?)))",
                    CommissionRule.Category.SALES.getCode(), productId,
                    CommissionRule.Category.PACKAGE.getCode(), packageId);
        } else if (packageId != null) {
            query.and("rule_category = ?", CommissionRule.Category.PACKAGE.getCode());
            query.and("(target_id IS NULL OR target_id = ?)", packageId);
        } else {
            query.and("rule_category = ?", CommissionRule.Category.SALES.getCode());
            query.and("(target_id IS NULL OR target_id = ?)", productId);
        }
        if (staffId != null) {
            query.and("(staff_id IS NULL OR staff_id = ?)", staffId);
        }
        List<CommissionRule> rules = mapper.selectListByQuery(query);
        return rules.stream()
                .sorted(Comparator
                        .comparing((CommissionRule r) ->
                                packageId != null && Integer.valueOf(CommissionRule.Category.PACKAGE.getCode()).equals(r.getRuleCategory()) ? 1 : 0
                        ).reversed()
                        .thenComparing((CommissionRule r) -> r.getStaffId() != null ? 1 : 0, Comparator.reverseOrder())
                        .thenComparing(r -> r.getTargetId() != null ? 1 : 0, Comparator.reverseOrder())
                        .thenComparing(CommissionRule::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CommissionRule::getId))
                .findFirst();
    }

    @Override
    public List<CommissionRule> findRechargeRules(BigDecimal amount) {
        QueryWrapper query = QueryWrapper.create()
                .where("rule_category = ?", CommissionRule.Category.RECHARGE.getCode())
                .and("enabled = 1");
        return mapper.selectListByQuery(query);
    }

    @Override
    public List<CommissionRule> query(Integer category, Long targetId, Long staffId, Integer enabled) {
        QueryWrapper query = QueryWrapper.create();
        if (category != null) {
            query.where("rule_category = ?", category);
        }
        if (targetId != null) {
            query.and("target_id = ?", targetId);
        }
        if (staffId != null) {
            query.and("staff_id = ?", staffId);
        }
        if (enabled != null) {
            query.and("enabled = ?", enabled);
        }
        return mapper.selectListByQuery(query);
    }

    @Override
    public long countEnabledByCategoryAndTargetId(Integer category, Long targetId) {
        QueryWrapper query = QueryWrapper.create()
                .where("rule_category = ?", category)
                .and("target_id = ?", targetId)
                .and("enabled = 1");
        return mapper.selectCountByQuery(query);
    }

    @Override
    public void disableById(Long id) {
        CommissionRule rule = mapper.selectOneById(id);
        if (rule != null) {
            rule.setEnabled(0);
            mapper.update(rule);
        }
    }
}
