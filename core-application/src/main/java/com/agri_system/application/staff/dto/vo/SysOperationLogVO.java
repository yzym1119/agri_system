package com.agri_system.application.staff.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "操作日志VO")
public class SysOperationLogVO {

    private Long id;
    private Long staffId;
    private String module;
    private String action;
    private String method;
    private String requestUri;
    private String requestParams;
    private String ip;
    private Long costTime;
    private Integer status;
    private String errorMsg;
    private String traceId;
    private LocalDateTime createdAt;
}





