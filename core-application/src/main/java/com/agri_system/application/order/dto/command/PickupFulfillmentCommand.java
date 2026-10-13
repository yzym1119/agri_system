package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/**
 * 库房取货履约命令。
 *
 * <p>扫描内部批次/货位标签产生 SCANNED 证据；无法扫描时由拣货员显式确认批次与货位，
 * 产生 FULFILLMENT_CONFIRMED 证据。仅表示 SKU 的 EAN/GS1 条码不会匹配内部批次标签。</p>
 */
@Data
@Schema(description = "库房取货履约命令")
public class PickupFulfillmentCommand {

    @Schema(description = "扫描到的内部批次标签（与批次+货位二选一）", example = "INTERNAL-BATCH-20260701-1")
    private String scannedCode;

    @Schema(description = "逐批核对后确认的全部已出库分配ID，不允许遗漏、重复或混入其他请求")
    private List<Long> allocationIds;

    @Schema(description = "拣货员显式确认的收货批次ID", example = "61")
    private Long batchId;

    @Schema(description = "拣货员显式确认的源货位ID", example = "12")
    private Long locationId;
}
