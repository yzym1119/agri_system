package com.agri_system.application.performance.service;

import com.agri_system.application.performance.dto.StaffCommissionDashboardVO;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.StaffMonthlyGoal;
import com.agri_system.domain.performance.repository.StaffMonthlyGoalRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;

/**
 * 员工提成看板服务
 * 聚合订单、提成、目标、客户数据，实时展示员工收入情况
 */
@Service
@RequiredArgsConstructor
public class StaffCommissionDashboardService {

    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final StaffMonthlyGoalRepository staffMonthlyGoalRepository;
    private final CustomerRepository customerRepository;

    public StaffCommissionDashboardVO getDashboard(Long staffId) {
        StaffCommissionDashboardVO vo = new StaffCommissionDashboardVO();

        // 基本信息
        Staff staff = staffRepository.findById(staffId).orElse(null);
        if (staff == null) {
            return vo;
        }
        vo.setStaffName(staff.getName());
        vo.setPosition(staff.getPosition());

        Store store = storeRepository.findById(staff.getStoreId()).orElse(null);
        vo.setStoreName(store != null ? store.getName() : "");

        // 时间范围
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);

        // 今日数据
        BigDecimal todaySales = orderRepository.sumPayAmountByStaffAndDateRange(staffId, todayStart, todayEnd);
        Integer todayOrders = orderRepository.countByStaffAndDateRange(staffId, todayStart, todayEnd);
        vo.setTodaySalesAmount(todaySales != null ? todaySales : BigDecimal.ZERO);
        vo.setTodayOrderCount(todayOrders != null ? todayOrders : 0);

        // 本月数据
        BigDecimal monthSales = orderRepository.sumPayAmountByStaffAndDateRange(staffId, monthStart, monthEnd);
        Integer monthOrders = orderRepository.countByStaffAndDateRange(staffId, monthStart, monthEnd);
        vo.setMonthSalesAmount(monthSales != null ? monthSales : BigDecimal.ZERO);
        vo.setMonthOrderCount(monthOrders != null ? monthOrders : 0);

        // 月度目标
        String monthStr = currentMonth.toString(); // "2026-05"
        staffMonthlyGoalRepository.findByStaffAndMonth(staffId, monthStr).ifPresent(goal -> {
            vo.setTargetSalesAmount(goal.getTargetSalesAmount());
            vo.setTargetNewCustomer(goal.getTargetNewCustomer());
            if (goal.getTargetSalesAmount() != null && goal.getTargetSalesAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rate = vo.getMonthSalesAmount()
                        .divide(goal.getTargetSalesAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
                vo.setSalesTargetRate(rate);
            }
        });

        // 底薪和预估工资
        BigDecimal baseSalary = staff.getSalaryBase() != null ? staff.getSalaryBase() : BigDecimal.ZERO;
        vo.setSalaryBase(baseSalary);
        BigDecimal todayCommission = estimateDailyCommission(staffId, todayStart, todayEnd);
        vo.setTodayCommission(todayCommission);

        BigDecimal monthCommission = estimateMonthlyCommission(staffId, monthStart, monthEnd);
        vo.setMonthCommission(monthCommission);
        vo.setEstimatedSalary(baseSalary.add(monthCommission));

        // 归属客户
        long bindCount = customerRepository.countByBindStaffId(staffId);
        vo.setBindCustomerCount((int) bindCount);

        // 最近提成记录
        vo.setRecentRecords(new ArrayList<>());

        return vo;
    }

    /**
     * 估算每日提成（简化：按销售额的固定比例估算，后续可接入实际提成规则）
     */
    private BigDecimal estimateDailyCommission(Long staffId, LocalDateTime start, LocalDateTime end) {
        BigDecimal sales = orderRepository.sumPayAmountByStaffAndDateRange(staffId, start, end);
        if (sales == null || sales.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        // 默认按2%估算提成（实际应按规则引擎计算）
        return sales.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 估算每月提成
     */
    private BigDecimal estimateMonthlyCommission(Long staffId, LocalDateTime start, LocalDateTime end) {
        BigDecimal sales = orderRepository.sumPayAmountByStaffAndDateRange(staffId, start, end);
        if (sales == null || sales.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return sales.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
    }
}
