package com.agri_system.domain.system.log.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("sys_operation_log")
public class SysOperationLog {

    @Id(keyType = KeyType.Auto)
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





