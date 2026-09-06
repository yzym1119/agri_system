package com.agri_system.application.performance.service;

import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 工资单明细口径回归:明细行必须与账单 totalCommission(sumPayableByFreezeMonth)同口径,
 * 即 status=2 且 freeze_until 落在账单月,而不是按 created_at 月。
 * 否则冻结开启后详情页明细行加起来会对不上总额。
 */
@ExtendWith(MockitoExtension.class)
class BillDetailLineItemsFreezeTest {

    @Mock StaffSalaryBillRepository staffSalaryBillRepository;
    @Mock StaffPerformanceLogRepository staffPerformanceLogRepository;
    @Mock StaffRepository staffRepository;
    @InjectMocks PerformanceApplicationService service;

    @Test
    void billDetailLineItemsUseFreezeMonthNotCreatedAt() {
        StaffSalaryBill bill = new StaffSalaryBill();
        bill.setId(1L);
        bill.setStaffId(2L);
        bill.setMonth("2026-08");
        when(staffSalaryBillRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(staffPerformanceLogRepository.findPayableByStaffAndFreezeMonth(2L, "2026-08"))
                .thenReturn(List.of(new StaffPerformanceLog()));

        service.getBillDetail(1L);

        // 明细走 freeze_until 口径(与账单 totalCommission 一致)
        verify(staffPerformanceLogRepository).findPayableByStaffAndFreezeMonth(2L, "2026-08");
        // 不再走 created_at 口径
        verify(staffPerformanceLogRepository, never()).findByStaffAndMonthRange(anyLong(), anyString());
    }
}
