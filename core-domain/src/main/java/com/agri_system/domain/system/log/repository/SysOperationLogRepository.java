package com.agri_system.domain.system.log.repository;

import com.agri_system.domain.system.log.model.SysOperationLog;

import java.util.List;

public interface SysOperationLogRepository {

    SysOperationLog save(SysOperationLog log);

    List<SysOperationLog> query(Long staffId, String module, int offset, int limit);

    long count(Long staffId, String module);
}





