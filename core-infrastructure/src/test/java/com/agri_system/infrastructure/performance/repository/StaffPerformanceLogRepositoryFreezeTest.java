package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.model.PerformanceLogStatus;
import com.agri_system.infrastructure.performance.mapper.StaffPerformanceLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StaffPerformanceLogRepositoryFreezeTest {

    @Test
    void unfreezeDueSetsUnfrozenAndReturnsCount() {
        StaffPerformanceLogMapper mapper = mock(StaffPerformanceLogMapper.class);
        when(mapper.updateByQuery(any(StaffPerformanceLog.class), any(QueryWrapper.class))).thenReturn(7);
        StaffPerformanceLogRepositoryImpl repo = new StaffPerformanceLogRepositoryImpl(mapper);

        int n = repo.unfreezeDue(LocalDateTime.now());

        assertEquals(7, n);
        ArgumentCaptor<StaffPerformanceLog> cap = ArgumentCaptor.forClass(StaffPerformanceLog.class);
        verify(mapper).updateByQuery(cap.capture(), any(QueryWrapper.class));
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), cap.getValue().getStatus());
    }

    @Test
    void sumPayableReturnsZeroWhenMapperReturnsNull() {
        StaffPerformanceLogMapper mapper = mock(StaffPerformanceLogMapper.class);
        when(mapper.selectOneByQueryAs(any(QueryWrapper.class), eq(BigDecimal.class))).thenReturn(null);
        StaffPerformanceLogRepositoryImpl repo = new StaffPerformanceLogRepositoryImpl(mapper);

        assertEquals(BigDecimal.ZERO, repo.sumPayableByFreezeMonth(1L, LocalDateTime.now(), LocalDateTime.now()));
    }

    @Test
    void findStaffIdsDelegatesToMapper() {
        StaffPerformanceLogMapper mapper = mock(StaffPerformanceLogMapper.class);
        StaffPerformanceLogRepositoryImpl repo = new StaffPerformanceLogRepositoryImpl(mapper);

        repo.findStaffIdsWithPayableInMonth("2026-08");

        verify(mapper).selectListByQueryAs(any(QueryWrapper.class), eq(Long.class));
    }
}
