package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.StaffPerformanceLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface StaffPerformanceLogRepository {

    StaffPerformanceLog save(StaffPerformanceLog log);

    void saveBatch(List<StaffPerformanceLog> logs);

    List<StaffPerformanceLog> findByOrderId(Long orderId);

    List<StaffPerformanceLog> findByBiz(Long bizId, Integer bizType);

    BigDecimal sumAmountByStaffAndPeriod(Long staffId, LocalDateTime start, LocalDateTime end);

    List<StaffPerformanceLog> findByStaffAndMonth(Long staffId, String month, int page, int size);

    List<Long> findStaffIdsByMonth(String month);
}

