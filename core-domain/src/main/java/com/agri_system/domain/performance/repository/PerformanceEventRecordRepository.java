package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.PerformanceEventRecord;

import java.util.Optional;

public interface PerformanceEventRecordRepository {

    boolean existsByEventKey(String eventKey);

    PerformanceEventRecord save(PerformanceEventRecord record);

    Optional<PerformanceEventRecord> findByEventKey(String eventKey);
}

