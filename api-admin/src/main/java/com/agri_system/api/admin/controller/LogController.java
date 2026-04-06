package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.common.PageResult;
import com.agri_system.application.staff.dto.query.OperationLogQuery;
import com.agri_system.application.staff.dto.vo.SysOperationLogVO;
import com.agri_system.application.staff.service.SysLogService;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/logs")
@Tag(name = "操作日志")
@RequiredArgsConstructor
public class LogController {

    private final SysLogService sysLogService;

    @GetMapping
    @Operation(summary = "分页查询操作日志")
    @SaCheckPermission("log:view")
    public R<PageResult<SysOperationLogVO>> list(@Valid OperationLogQuery query) {
        return R.success(sysLogService.query(query));
    }
}

