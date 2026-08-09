package com.agri_system.application.performance.job;

import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffCustomerRelationRepository;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceSettlementJob {

    private final StaffPerformanceLogRepository logRepository;
    private final StaffSalaryBillRepository salaryBillRepository;
    private final OrderRepository orderRepository;
    private final StaffCustomerRelationRepository relationRepository;

    /**
     * 每月1号04:00结算上月工资单
     */
    @Scheduled(cron = "0 0 4 1 * ?")
    @Transactional(rollbackFor = Exception.class)
    public void settleLastMonth() {
        YearMonth target = YearMonth.now().minusMonths(1);
        log.info("Start performance settlement for {}", target);
        List<Long> staffIds = logRepository.findStaffIdsWithPayableInMonth(target.toString());
        for (Long staffId : staffIds) {
            BigDecimal commission = logRepository.sumPayableByFreezeMonth(
                    staffId,
                    target.atDay(1).atStartOfDay(),
                    target.plusMonths(1).atDay(1).atStartOfDay().minusNanos(1));
            BigDecimal sales = orderRepository.sumPayAmountByStaffAndMonth(staffId, target.toString());
            long newCustomer = relationRepository.countFirstEffectiveByMonth(staffId, target.toString());

            StaffSalaryBill bill = salaryBillRepository.findByStaffAndMonth(staffId, target.toString())
                    .orElse(new StaffSalaryBill());
            bill.setStaffId(staffId);
            bill.setMonth(target.toString());
            bill.setTotalCommission(commission);
            bill.setTotalSales(sales);
            bill.setNewCustomerCount((int) newCustomer);
            bill.setStatus(0);
            bill.setCreatedAt(LocalDateTime.now());
            salaryBillRepository.save(bill);
        }
        log.info("Performance settlement completed for {}", target);
    }
}

