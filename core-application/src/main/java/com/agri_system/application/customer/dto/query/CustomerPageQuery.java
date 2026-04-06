package com.agri_system.application.customer.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户分页查询条件
 *
 * @author AI Generated
 */
@Data
@Schema(description = "客户分页查询条件")
public class CustomerPageQuery {

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "客户姓名（模糊查询，可选）")
    private String name;

    @Schema(description = "手机号（模糊查询，可选）")
    private String phone;

    @Schema(description = "客户类型：1-普通，2-VIP", example = "2")
    private Integer type;

    @Schema(description = "状态：1-正常，2-停用", example = "1")
    private Integer status;

    @Schema(description = "归属员工ID（可选）", example = "1")
    private Long bindStaffId;
}









