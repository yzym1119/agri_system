package com.agri_system.infrastructure.system.log.repository;

import com.agri_system.domain.system.log.model.SysOperationLog;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.infrastructure.system.log.mapper.SysOperationLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SysOperationLogRepositoryImpl implements SysOperationLogRepository {

    private final SysOperationLogMapper logMapper;

    @Override
    public SysOperationLog save(SysOperationLog log) {
        if (log.getId() == null) {
            logMapper.insert(log);
        } else {
            logMapper.update(log);
        }
        return log;
    }

    @Override
    public List<SysOperationLog> query(Long staffId, String module, int offset, int limit) {
        QueryWrapper query = QueryWrapper.create();
        boolean hasCondition = false;
        if (staffId != null) {
            query.where("staff_id = ?", staffId);
            hasCondition = true;
        }
        if (module != null && !module.isBlank()) {
            if (hasCondition) {
                query.and("module = ?", module);
            } else {
                query.where("module = ?", module);
                hasCondition = true;
            }
        }
        query.orderBy("created_at DESC")
                .limit(offset, limit);
        return logMapper.selectListByQuery(query);
    }

    @Override
    public long count(Long staffId, String module) {
        QueryWrapper query = QueryWrapper.create();
        boolean hasCondition = false;
        if (staffId != null) {
            query.where("staff_id = ?", staffId);
            hasCondition = true;
        }
        if (module != null && !module.isBlank()) {
            if (hasCondition) {
                query.and("module = ?", module);
            } else {
                query.where("module = ?", module);
            }
        }
        return logMapper.selectCountByQuery(query);
    }
}

