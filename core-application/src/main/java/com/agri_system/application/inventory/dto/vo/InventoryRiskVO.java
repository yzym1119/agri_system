package com.agri_system.application.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库存风险提示VO（零售事实链 Task8）。
 *
 * <p>日期、库存与阈值只做成<b>可解释的提示</b>：未知日期显示为未知（既不安全也不阻断），
 * 临期/过期仅提示；本VO不代表任何自动执行动作，人工决定单独追加留痕。</p>
 */
@Data
public class InventoryRiskVO {

    /** 风险类型：DATE_UNKNOWN/EXPIRING/EXPIRED/LOW_STOCK/OVERSTOCK/LOW_MARGIN/DATA_INSUFFICIENT。 */
    private String kind;

    private Long productId;

    private String productName;

    private String productCode;

    private Long storeId;

    /** 日期风险所属收货批次；商品级风险为 null。 */
    private Long batchId;

    /** 内部收货批次号（库房标签）。 */
    private String internalReceiptBatchNo;

    /** 当前可用库存（基准单位 = base - reserved）；数据不足时为 null，绝不按 0 当作安全。 */
    private BigDecimal currentStock;

    /** 商品已配置的最低库存；未配置为 null。 */
    private BigDecimal minStock;

    /** 商品已配置的最高库存；未配置为 null。 */
    private BigDecimal maxStock;

    /** 日期状态：KNOWN/UNKNOWN；非日期风险为 null。 */
    private String dateStatus;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    /** 距到期天数：负数表示已过期；日期未知为 null。 */
    private Integer daysToExpiry;

    /** 观察窗口天数（销售/毛利口径）。 */
    private Integer observationWindowDays;

    /** 观察窗口内的证据数量（订单数）；无证据为 0。 */
    private Integer evidenceCount;

    /** 数据置信度：SUFFICIENT_DATA/INSUFFICIENT_DATA。 */
    private String confidence;

    /** 人类可读的解释说明（含批次/窗口/口径，便于审核）。 */
    private String message;

    /**
     * 风险类型的流式访问器（与 {@code getKind()} 同值）。
     *
     * <p>绑定用例以 {@code InventoryRiskVO::kind} 提取风险类型，需保留无前缀访问器；
     * JSON 序列化仍走 Lombok 生成的 {@code getKind()}。</p>
     */
    public String kind() {
        return kind;
    }

    /** 批次ID的流式访问器（与 {@code getBatchId()} 同值；绑定用例以 {@code InventoryRiskVO::batchId} 提取）。 */
    public Long batchId() {
        return batchId;
    }
}
