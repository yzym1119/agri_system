package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffPerformanceLog;
import com.agri_system.domain.performance.model.PerformanceLogStatus;
import com.agri_system.domain.performance.repository.StaffPerformanceLogRepository;
import com.agri_system.infrastructure.performance.mapper.StaffPerformanceLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StaffPerformanceLogRepositoryImpl implements StaffPerformanceLogRepository {

    private final StaffPerformanceLogMapper mapper;

    @Override
    public StaffPerformanceLog save(StaffPerformanceLog log) {
        if (log.getId() == null) {
            mapper.insert(log);
        } else {
            mapper.update(log);
        }
        return log;
    }

    @Override
    public void saveBatch(List<StaffPerformanceLog> logs) {
        logs.forEach(mapper::insert);
    }

    @Override
    public List<StaffPerformanceLog> findByOrderId(Long orderId) {
        QueryWrapper query = QueryWrapper.create()
                .where("biz_id = ?", orderId)
                .and("biz_type = ?", 1);
        return mapper.selectListByQuery(query);
    }

    @Override
    public List<StaffPerformanceLog> findByBiz(Long bizId, Integer bizType) {
        QueryWrapper query = QueryWrapper.create()
                .where("biz_id = ?", bizId)
                .and("biz_type = ?", bizType);
        return mapper.selectListByQuery(query);
    }

    @Override
    public BigDecimal sumAmountByStaffAndPeriod(Long staffId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper query = QueryWrapper.create()
                .select("COALESCE(SUM(amount),0) AS total")
                .where("staff_id = ?", staffId)
                .and("created_at BETWEEN ? AND ?", start, end);
        BigDecimal result = mapper.selectOneByQueryAs(query, BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public List<StaffPerformanceLog> findByStaffAndMonth(Long staffId, String month, int page, int size) {
        QueryWrapper query = QueryWrapper.create()
                .where("staff_id = ?", staffId)
                .and("DATE_FORMAT(created_at,'%Y-%m') = ?", month)
                .orderBy("created_at DESC")
                .limit((page - 1) * size, size);
        return mapper.selectListByQuery(query);
    }

    @Override
    public List<Long> findStaffIdsByMonth(String month) {
        QueryWrapper query = QueryWrapper.create()
                .select("DISTINCT staff_id")
                .where("DATE_FORMAT(created_at,'%Y-%m') = ?", month);
        return mapper.selectListByQueryAs(query, Long.class);
    }

    @Override
    public List<StaffPerformanceLog> findByStaffAndMonthRange(Long staffId, String month) {
        QueryWrapper query = QueryWrapper.create()
                .where("staff_id = ?", staffId)
                .and("DATE_FORMAT(created_at,'%Y-%m') = ?", month)
                .orderBy("created_at DESC");
        return mapper.selectListByQuery(query);
    }

    @Override
    public int unfreezeDue(LocalDateTime now) {
        StaffPerformanceLog update = new StaffPerformanceLog();
        update.setStatus(PerformanceLogStatus.UNFROZEN.getCode());
        QueryWrapper query = QueryWrapper.create()
                .where("status = ?", PerformanceLogStatus.FROZEN.getCode())
                .and("freeze_until <= ?", now);
        return mapper.updateByQuery(update, query);
    }

    @Override
    public java.math.BigDecimal sumPayableByFreezeMonth(Long staffId, LocalDateTime start, LocalDateTime end) {
        QueryWrapper query = QueryWrapper.create()
                .select("COALESCE(SUM(amount),0) AS total")
                .where("staff_id = ?", staffId)
                .and("status = ?", PerformanceLogStatus.UNFROZEN.getCode())
                .and("freeze_until BETWEEN ? AND ?", start, end);
        java.math.BigDecimal result = mapper.selectOneByQueryAs(query, java.math.BigDecimal.class);
        return result == null ? java.math.BigDecimal.ZERO : result;
    }

    @Override
    public java.util.List<Long> findStaffIdsWithPayableInMonth(String month) {
        QueryWrapper query = QueryWrapper.create()
                .select("DISTINCT staff_id")
                .where("status = ?", PerformanceLogStatus.UNFROZEN.getCode())
                .and("DATE_FORMAT(freeze_until,'%Y-%m') = ?", month);
        return mapper.selectListByQueryAs(query, Long.class);
    }
}

