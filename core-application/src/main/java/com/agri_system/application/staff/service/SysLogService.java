package com.agri_system.application.staff.service;

import com.agri_system.application.common.PageResult;
import com.agri_system.application.staff.dto.command.SysOperationLogCommand;
import com.agri_system.application.staff.dto.query.OperationLogQuery;
import com.agri_system.application.staff.dto.vo.SysOperationLogVO;
import com.agri_system.domain.system.log.model.SysOperationLog;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysOperationLogRepository logRepository;

    @Async
    public void recordAsync(SysOperationLogCommand command) {
        SysOperationLog log = new SysOperationLog();
        log.setStaffId(command.getStaffId());
        log.setModule(command.getModule());
        log.setAction(command.getAction());
        log.setMethod(command.getMethod());
        log.setRequestUri(command.getRequestUri());
        log.setRequestParams(command.getRequestParams());
        log.setIp(command.getIp());
        log.setCostTime(command.getCostTime());
        log.setStatus(command.getStatus());
        log.setErrorMsg(command.getErrorMsg());
        log.setTraceId(command.getTraceId());
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    public PageResult<SysOperationLogVO> query(OperationLogQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 20 : query.getPageSize();
        int offset = (pageNum - 1) * pageSize;
        List<SysOperationLog> logs = logRepository.query(query.getStaffId(), query.getModule(), offset, pageSize);
        long total = logRepository.count(query.getStaffId(), query.getModule());
        List<SysOperationLogVO> vos = logs.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, vos);
    }

    private SysOperationLogVO toVO(SysOperationLog log) {
        SysOperationLogVO vo = new SysOperationLogVO();
        vo.setId(log.getId());
        vo.setStaffId(log.getStaffId());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setMethod(log.getMethod());
        vo.setRequestUri(log.getRequestUri());
        vo.setRequestParams(log.getRequestParams());
        vo.setIp(log.getIp());
        vo.setCostTime(log.getCostTime());
        vo.setStatus(log.getStatus());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setTraceId(log.getTraceId());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}





