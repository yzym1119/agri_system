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

    List<StaffPerformanceLog> findByStaffAndMonthRange(Long staffId, String month);

    /** 到期解冻:把 status=1 且 freeze_until<=now 的行置为 status=2,返回受影响行数 */
    int unfreezeDue(LocalDateTime now);

    /** 按 freeze_until 月份聚合可发放(status=2)提成 */
    java.math.BigDecimal sumPayableByFreezeMonth(Long staffId, LocalDateTime start, LocalDateTime end);

    /** 找出 freeze_until 落在指定月份、且有可发放提成的员工 */
    java.util.List<Long> findStaffIdsWithPayableInMonth(String month);
}

