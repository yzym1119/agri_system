package com.agri_system.application.performance.job;

import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffCustomerRelationRepository;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerformanceSettlementFreezeTest {

    @Mock StaffPerformanceLogRepository logRepository;
    @Mock StaffSalaryBillRepository salaryBillRepository;
    @Mock OrderRepository orderRepository;
    @Mock StaffCustomerRelationRepository relationRepository;

    @InjectMocks PerformanceSettlementJob job;

    @Test
    void usesFreezeMonthAggregation() {
        when(logRepository.findStaffIdsWithPayableInMonth(anyString())).thenReturn(List.of(2L));
        when(logRepository.sumPayableByFreezeMonth(eq(2L), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(orderRepository.sumPayAmountByStaffAndMonth(eq(2L), anyString())).thenReturn(new BigDecimal("500.00"));
        when(relationRepository.countFirstEffectiveByMonth(eq(2L), anyString())).thenReturn(1L);
        when(salaryBillRepository.findByStaffAndMonth(eq(2L), anyString())).thenReturn(Optional.of(new StaffSalaryBill()));
        when(salaryBillRepository.save(any())).thenReturn(null);

        job.settleLastMonth();

        // 用新方法
        verify(logRepository).findStaffIdsWithPayableInMonth(anyString());
        verify(logRepository).sumPayableByFreezeMonth(eq(2L), any(), any());
        // 不再用旧方法
        verify(logRepository, never()).findStaffIdsByMonth(anyString());
        verify(logRepository, never()).sumAmountByStaffAndPeriod(anyLong(), any(), any());
    }
}
