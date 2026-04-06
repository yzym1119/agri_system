package com.agri_system.infrastructure.performance.mapper;

import com.agri_system.domain.performance.model.PerformanceEventRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PerformanceEventRecordMapper extends BaseMapper<PerformanceEventRecord> {
}

