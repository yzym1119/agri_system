package com.agri_system.application.performance.service;

import com.agri_system.application.performance.dto.StaffDashboardVO;
import com.agri_system.application.performance.dto.StaffPerformanceLogVO;
import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffPerformanceApplicationService {

    private final StaffPerformanceLogRepository logRepository;
    private final StaffSalaryBillRepository salaryBillRepository;

    public StaffDashboardVO dashboard(Long staffId) {
        YearMonth now = YearMonth.now();
        String month = now.toString();
        LocalDateTime start = now.atDay(1).atStartOfDay();
        LocalDateTime end = now.plusMonths(1).atDay(1).atStartOfDay().minusNanos(1);
        var billOpt = salaryBillRepository.findByStaffAndMonth(staffId, month);
        BigDecimal commission = logRepository.sumAmountByStaffAndPeriod(staffId, start, end);

        StaffDashboardVO vo = new StaffDashboardVO();
        vo.setMonthCommission(commission);
        vo.setMonthSales(billOpt.map(b -> b.getTotalSales()).orElse(BigDecimal.ZERO));
        vo.setNewCustomerCount(billOpt.map(b -> b.getNewCustomerCount()).orElse(0));
        vo.setTargetSalesAmount(BigDecimal.ZERO);
        vo.setTargetCommission(BigDecimal.ZERO);
        vo.setGoalProgress(BigDecimal.ZERO);
        return vo;
    }

    public List<StaffPerformanceLogVO> listLogs(Long staffId, String month, int page, int size) {
        if (month == null || month.isBlank()) {
            YearMonth now = YearMonth.now();
            month = now.toString();
        }
        List<StaffPerformanceLog> logs = logRepository.findByStaffAndMonth(staffId, month, page, size);
        return logs.stream().map(this::toVO).collect(Collectors.toList());
    }

    private StaffPerformanceLogVO toVO(StaffPerformanceLog log) {
        StaffPerformanceLogVO vo = new StaffPerformanceLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}

