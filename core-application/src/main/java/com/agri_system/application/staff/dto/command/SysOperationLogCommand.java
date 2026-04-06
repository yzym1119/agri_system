package com.agri_system.application.staff.dto.command;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SysOperationLogCommand {

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
}





