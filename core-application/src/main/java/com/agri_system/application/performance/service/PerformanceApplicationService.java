package com.agri_system.application.performance.service;

import com.agri_system.application.performance.dto.*;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.model.StaffMonthlyGoal;
import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.repository.CommissionRuleRepository;
import com.agri_system.domain.performance.repository.StaffMonthlyGoalRepository;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
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
    private final StaffPerformanceLogRepository staffPerformanceLogRepository;
    private final ProductRepository productRepository;
    private final ProductPackageRepository productPackageRepository;
    private final StaffRepository staffRepository;

    @Transactional(rollbackFor = Exception.class)
    public Long createOrUpdateRule(CommissionRuleCreateCommand command) {
        validateRuleCommand(command);

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
        if (rule.getStatus() == null) {
            rule.setStatus(1); // 默认启用状态
        }
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
        if (bill.getStatus() != null && bill.getStatus() != 0) {
            throw new IllegalStateException("工资单已确认,不可重复确认:" + billId);
        }
        bill.setStatus(1);
        bill.setConfirmedAt(java.time.LocalDateTime.now());
        staffSalaryBillRepository.save(bill);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long ruleId) {
        commissionRuleRepository.disableById(ruleId);
    }

    public GoalPageResult listGoals(Long staffId, String monthFrom, String monthTo, int page, int size) {
        List<StaffMonthlyGoal> goals = staffMonthlyGoalRepository.query(staffId, monthFrom, monthTo, page, size);
        long total = staffMonthlyGoalRepository.count(staffId, monthFrom, monthTo);

        GoalPageResult result = new GoalPageResult();
        result.setRecords(goals.stream().map(this::toGoalItem).collect(Collectors.toList()));
        result.setTotal(total);
        return result;
    }

    public StaffSalaryBillDetailVO getBillDetail(Long billId) {
        StaffSalaryBill bill = staffSalaryBillRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("工资单不存在"));

        StaffSalaryBillDetailVO vo = new StaffSalaryBillDetailVO();
        BeanUtils.copyProperties(bill, vo);

        // Resolve staff name
        staffRepository.findById(bill.getStaffId())
                .ifPresent(staff -> vo.setStaffName(staff.getName()));

        // Load line items (performance logs for this staff in this month)
        // 明细口径与账单 totalCommission(sumPayableByFreezeMonth)保持一致:
        // status=2 且 freeze_until 落在账单月,避免冻结开启后明细行与总额对不上
        List<StaffPerformanceLog> logs = staffPerformanceLogRepository.findPayableByStaffAndFreezeMonth(
                bill.getStaffId(), bill.getMonth());
        vo.setLineItems(logs.stream().map(this::toLogVO).collect(Collectors.toList()));

        return vo;
    }

    private GoalPageResult.GoalItem toGoalItem(StaffMonthlyGoal goal) {
        GoalPageResult.GoalItem item = new GoalPageResult.GoalItem();
        item.setId(goal.getId());
        item.setStaffId(goal.getStaffId());
        item.setMonth(goal.getMonth());
        item.setTargetSalesAmount(goal.getTargetSalesAmount());
        item.setTargetNewCustomer(goal.getTargetNewCustomer());
        item.setTargetCommission(goal.getTargetCommission());
        item.setCreatedAt(goal.getCreatedAt());

        staffRepository.findById(goal.getStaffId())
                .ifPresent(staff -> item.setStaffName(staff.getName()));
        return item;
    }

    private StaffPerformanceLogVO toLogVO(StaffPerformanceLog log) {
        StaffPerformanceLogVO vo = new StaffPerformanceLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private CommissionRuleVO toRuleVO(CommissionRule rule) {
        CommissionRuleVO vo = new CommissionRuleVO();
        BeanUtils.copyProperties(rule, vo);
        vo.setTargetName(resolveTargetName(rule));
        return vo;
    }

    private StaffSalaryBillVO toBillVO(StaffSalaryBill bill) {
        StaffSalaryBillVO vo = new StaffSalaryBillVO();
        BeanUtils.copyProperties(bill, vo);
        return vo;
    }

    private void validateRuleCommand(CommissionRuleCreateCommand command) {
        if (command.getStaffId() != null) {
            Staff staff = staffRepository.findById(command.getStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("指定员工不存在"));
            if (staff.hasBeenDeleted() || !staff.isActive()) {
                throw new IllegalArgumentException("指定员工不是有效的在职员工");
            }
        }

        if (CommissionRule.Category.SALES.getCode() == command.getRuleCategory()) {
            if (command.getTargetId() == null) {
                throw new IllegalArgumentException("销售提成规则必须选择商品");
            }
            Product product = productRepository.findById(command.getTargetId())
                    .orElseThrow(() -> new IllegalArgumentException("提成规则指定的商品不存在"));
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new IllegalArgumentException("提成规则指定的商品已停用");
            }
        }

        if (CommissionRule.Category.PACKAGE.getCode() == command.getRuleCategory()) {
            if (command.getTargetId() == null) {
                throw new IllegalArgumentException("套餐提成规则必须选择套餐");
            }
            ProductPackage productPackage = productPackageRepository.findById(command.getTargetId())
                    .orElseThrow(() -> new IllegalArgumentException("提成规则指定的套餐不存在"));
            if (productPackage.isDeleted()) {
                throw new IllegalArgumentException("提成规则指定的套餐已删除");
            }
        }

        if (CommissionRule.Category.RECHARGE.getCode() == command.getRuleCategory()
                || CommissionRule.Category.NEW_CUSTOMER.getCode() == command.getRuleCategory()) {
            if (command.getTargetId() != null) {
                throw new IllegalArgumentException("当前规则类别不支持配置目标对象");
            }
        }

        if (CommissionRule.Category.BRAND_PROMOTION.getCode() == command.getRuleCategory()) {
            throw new IllegalArgumentException("品牌推广规则暂未接通结算链路，暂不支持配置");
        }
    }

    private String resolveTargetName(CommissionRule rule) {
        if (rule.getTargetId() == null) {
            if (CommissionRule.Category.RECHARGE.getCode() == rule.getRuleCategory()
                    || CommissionRule.Category.NEW_CUSTOMER.getCode() == rule.getRuleCategory()) {
                return "全局规则";
            }
            if (CommissionRule.Category.BRAND_PROMOTION.getCode() == rule.getRuleCategory()) {
                return "预留类别";
            }
            return null;
        }
        if (CommissionRule.Category.SALES.getCode() == rule.getRuleCategory()) {
            return productRepository.findById(rule.getTargetId())
                    .map(Product::getName)
                    .orElse("商品#" + rule.getTargetId());
        }
        if (CommissionRule.Category.PACKAGE.getCode() == rule.getRuleCategory()) {
            return productPackageRepository.findById(rule.getTargetId())
                    .map(ProductPackage::getName)
                    .orElse("套餐#" + rule.getTargetId());
        }
        return "目标#" + rule.getTargetId();
    }
}
