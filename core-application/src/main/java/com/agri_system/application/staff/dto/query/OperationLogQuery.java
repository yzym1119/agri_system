package com.agri_system.application.staff.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "操作日志查询条件")
public class OperationLogQuery {

    private Long staffId;

    private String module;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}





