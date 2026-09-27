package com.agri_system.domain.inventory.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存风险人工决策留痕（追加写）。
 *
 * <p>记录人工对日期/库存/阈值提示选择的动作、原因与操作人；表由 V120 的触发器保证
 * 追加写，绝不更新或删除。留痕本身不触发任何销售/调整/采购/调拨动作。</p>
 */
@Data
@Table("inventory_decision_log")
public class InventoryDecisionLog {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 风险类型：DATE_UNKNOWN/EXPIRING/EXPIRED/LOW_STOCK/OVERSTOCK/LOW_MARGIN/DATA_INSUFFICIENT。 */
    private String riskKind;

    /** 风险对象类型：PRODUCT 或 BATCH。 */
    private String entityType;

    private Long entityId;

    /** 提示建议（advisory，可为空）。 */
    private String suggestedAction;

    /** 人工选择的动作。 */
    private String decisionAction;

    private String decisionReason;

    private Long operatorId;

    private Long confirmerId;

    private LocalDateTime decidedAt;

    private LocalDateTime createdAt;
}
