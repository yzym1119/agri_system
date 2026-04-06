package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.PerformanceEventRecord;
import com.agri_system.domain.performance.repository.PerformanceEventRecordRepository;
import com.agri_system.infrastructure.performance.mapper.PerformanceEventRecordMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PerformanceEventRecordRepositoryImpl implements PerformanceEventRecordRepository {

    private final PerformanceEventRecordMapper mapper;

    @Override
    public boolean existsByEventKey(String eventKey) {
        return mapper.selectCountByQuery(QueryWrapper.create().where("event_key = ?", eventKey)) > 0;
    }

    @Override
    public PerformanceEventRecord save(PerformanceEventRecord record) {
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.update(record);
        }
        return record;
    }

    @Override
    public Optional<PerformanceEventRecord> findByEventKey(String eventKey) {
        return Optional.ofNullable(mapper.selectOneByQuery(QueryWrapper.create().where("event_key = ?", eventKey)));
    }
}

