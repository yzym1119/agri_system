package com.agri_system.application.performance.service;

import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmSalaryBillHardeningTest {

    @Mock StaffSalaryBillRepository staffSalaryBillRepository;
    @InjectMocks PerformanceApplicationService service;

    @Test
    void confirmDraftBillSucceeds() {
        StaffSalaryBill bill = new StaffSalaryBill();
        bill.setId(1L);
        bill.setStatus(0);
        when(staffSalaryBillRepository.findById(1L)).thenReturn(Optional.of(bill));

        service.confirmSalaryBill(1L);

        assertEquals(1, bill.getStatus());
        assertNotNull(bill.getConfirmedAt());
        verify(staffSalaryBillRepository).save(bill);
    }

    @Test
    void confirmAlreadyConfirmedThrows() {
        StaffSalaryBill bill = new StaffSalaryBill();
        bill.setId(1L);
        bill.setStatus(1);
        when(staffSalaryBillRepository.findById(1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class, () -> service.confirmSalaryBill(1L));
        verify(staffSalaryBillRepository, never()).save(any());
    }
}
