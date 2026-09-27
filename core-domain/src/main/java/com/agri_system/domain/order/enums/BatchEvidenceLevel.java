package com.agri_system.domain.order.enums;

/**
 * 销售行批次归属的证据等级。
 *
 * <p>SKU-only 的 EAN/GS1 条码绝不能升级为 {@link #SCANNED}；只有内部批次标签扫描或
 * 拣货员显式确认批次货位才产生强证据，货架混批销售允许保持 {@link #UNKNOWN} 且不阻断结账。</p>
 */
public enum BatchEvidenceLevel {
    /** 扫描内部批次/货位标签得到的强证据。 */
    SCANNED,
    /** 拣货员显式确认批次与货位得到的强证据。 */
    FULFILLMENT_CONFIRMED,
    /** 按配置规则（如货架单批次）自动归属。 */
    RULE_ALLOCATED,
    /** 混批货架销售等场景下的显式未知归属；允许结账。 */
    UNKNOWN
}
