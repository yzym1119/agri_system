package com.agri_system.application.performance.service;

import com.agri_system.application.performance.dto.*;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.model.StaffMonthlyGoal;
import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.CommissionRuleRepository;
import com.agri_system.domain.performance.repository.StaffMonthlyGoalRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceApplicationService {

    private final CommissionRuleRepository commissionRuleRepository;
    private final StaffMonthlyGoalRepository staffMonthlyGoalRepository;
    private final StaffSalaryBillRepository staffSalaryBillRepository;

    @Transactional(rollbackFor = Exception.class)
    public Long createOrUpdateRule(CommissionRuleCreateCommand command) {
        CommissionRule rule = new CommissionRule();
        if (command.getId() != null) {
            commissionRuleRepository.findById(command.getId())
                    .ifPresent(existing -> BeanUtils.copyProperties(existing, rule));
            rule.setId(command.getId());
        }
        rule.setRuleCategory(command.getRuleCategory());
        rule.setTargetId(command.getTargetId());
        rule.setStaffId(command.getStaffId());
        rule.setCalcMode(command.getCalcMode());
        rule.setCalcValue(command.getCalcValue());
        rule.setMaxPerItem(command.getMaxPerItem());
        rule.setMaxPerOrder(command.getMaxPerOrder());
        rule.setPriority(command.getPriority());
        rule.setEnabled(Boolean.TRUE.equals(command.getEnabled()) ? 1 : 0);
        rule.setRemark(command.getRemark());
        commissionRuleRepository.save(rule);
        return rule.getId();
    }

    public List<CommissionRuleVO> listRules(CommissionRuleQuery query) {
        List<CommissionRule> rules = commissionRuleRepository.query(
                query.getRuleCategory(), query.getTargetId(), query.getStaffId(), query.getEnabled());
        return rules.stream().map(this::toRuleVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void setMonthlyGoal(StaffMonthlyGoalCommand command) {
        StaffMonthlyGoal goal = staffMonthlyGoalRepository
                .findByStaffAndMonth(command.getStaffId(), command.getMonth())
                .orElse(new StaffMonthlyGoal());
        goal.setStaffId(command.getStaffId());
        goal.setMonth(command.getMonth());
        goal.setTargetSalesAmount(command.getTargetSalesAmount());
        goal.setTargetNewCustomer(command.getTargetNewCustomer());
        goal.setTargetCommission(command.getTargetCommission());
        staffMonthlyGoalRepository.save(goal);
    }

    public List<StaffSalaryBillVO> listSalaryBills(String month, Integer status, int page, int size) {
        List<StaffSalaryBill> bills = staffSalaryBillRepository.query(month, status, page, size);
        return bills.stream().map(this::toBillVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmSalaryBill(Long billId) {
        StaffSalaryBill bill = staffSalaryBillRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("工资单不存在"));
        bill.setStatus(1);
        bill.setConfirmedAt(java.time.LocalDateTime.now());
        staffSalaryBillRepository.save(bill);
    }

    private CommissionRuleVO toRuleVO(CommissionRule rule) {
        CommissionRuleVO vo = new CommissionRuleVO();
        BeanUtils.copyProperties(rule, vo);
        return vo;
    }

    private StaffSalaryBillVO toBillVO(StaffSalaryBill bill) {
        StaffSalaryBillVO vo = new StaffSalaryBillVO();
        BeanUtils.copyProperties(bill, vo);
        return vo;
    }
}

