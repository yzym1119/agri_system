package com.agri_system.application.performance.service;

import com.agri_system.application.performance.dto.StaffCommissionDashboardVO;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.repository.StaffMonthlyGoalRepository;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 员工提成看板:提成数字必须取 staff_performance_log 的真实产生口径,
 * 而不是旧的"销售额×2%"假估算;预估工资 = 底薪 + 本月真实提成。
 */
@ExtendWith(MockitoExtension.class)
class StaffCommissionDashboardServiceTest {

    @Mock StaffRepository staffRepository;
    @Mock StoreRepository storeRepository;
    @Mock OrderRepository orderRepository;
    @Mock StaffMonthlyGoalRepository staffMonthlyGoalRepository;
    @Mock CustomerRepository customerRepository;
    @Mock StaffPerformanceLogRepository staffPerformanceLogRepository;

    @InjectMocks StaffCommissionDashboardService service;

    @Test
    void commissionComesFromPerformanceLogNotSalesEstimate() {
        Long staffId = 7L;
        Staff staff = new Staff();
        staff.setId(staffId);
        staff.setName("张三");
        staff.setPosition("店员");
        staff.setStoreId(1L);
        staff.setSalaryBase(new BigDecimal("5000.00"));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));

        // 今日区间(同一天)返回 88.88,本月区间(跨月)返回 666.66;用区间是否同一天区分,不依赖当前日期
        when(staffPerformanceLogRepository.sumAmountByStaffAndPeriod(eq(staffId), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime start = inv.getArgument(1);
                    LocalDateTime end = inv.getArgument(2);
                    boolean isToday = start.toLocalDate().equals(end.toLocalDate());
                    return isToday ? new BigDecimal("88.88") : new BigDecimal("666.66");
                });

        StaffCommissionDashboardVO vo = service.getDashboard(staffId);

        // 提成来自真实 log,而非 销售额×0.02
        assertEquals(new BigDecimal("88.88"), vo.getTodayCommission());
        assertEquals(new BigDecimal("666.66"), vo.getMonthCommission());
        // 预估工资 = 底薪 5000.00 + 本月真实提成 666.66
        assertEquals(new BigDecimal("5666.66"), vo.getEstimatedSalary());
    }
}
