package com.agri_system.application.after_sales.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "售后详情")
public class AfterSalesDetailVO extends AfterSalesPageVO {

    @Schema(description = "售后明细列表")
    private List<AfterSalesItemVO> items;
}
