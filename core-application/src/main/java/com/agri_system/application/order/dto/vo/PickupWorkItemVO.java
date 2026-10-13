package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 取货待办工作项VO（零售事实链 Task9：库房取货待办查询）。
 *
 * <p>关联行缺失时标签字段为 null（如订单/商品/货位被删除），不阻断待办列表展示。</p>
 */
@Data
@Schema(description = "取货待办工作项VO")
public class PickupWorkItemVO {

    @Schema(description = "取货请求ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号；订单缺失时为 null")
    private String orderNo;

    @Schema(description = "订单明细ID")
    private Long orderItemId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品编码；商品缺失时为 null")
    private String productCode;

    @Schema(description = "商品名称；商品缺失时为 null")
    private String productName;

    @Schema(description = "请求拣货数量（基准单位）")
    private BigDecimal requestedBaseQuantity;

    @Schema(description = "期望源货位ID")
    private Long sourceLocationId;

    @Schema(description = "期望源货位名称；未指定或货位缺失时为 null")
    private String sourceLocationName;

    @Schema(description = "状态：PENDING/FULFILLED")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "请求所属的已出库批次分配，数量为销售时基准数量")
    private List<AllocationVO> allocations;

    @Data
    public static class AllocationVO {
        private Long id;
        private Long batchId;
        private String internalReceiptBatchNo;
        private Long locationId;
        private String locationName;
        private BigDecimal allocatedBaseQuantity;
    }
}
