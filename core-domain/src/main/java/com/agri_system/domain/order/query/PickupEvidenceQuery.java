package com.agri_system.domain.order.query;

import java.util.List;
import java.util.Map;

/**
 * 库房提货交付证据的只读查询：原 SALE 出库事实、同分配的正 RETURN 累计及处理中售后标记。
 * 只提供事实，交付资格判断与剩余量计算仍由应用层在订单锁内完成。
 */
public interface PickupEvidenceQuery {

    /** 批次持久化基准单位名；仅在恰好命中一条基准单位时返回，未命中或多条均为 null。 */
    String findBaseUnitName(Long baseUnitId, Long productId);

    /**
     * 一次批查源 SALE 移动与同分配正 RETURN，保留原 SQL 的 SELECT m.* 列集合与
     * processing_after_sales 派生列。调用方保证 allocationIds 非空（分配列表非空时才查询）。
     */
    List<Map<String, Object>> findMovements(List<Long> sourceMovementIds, List<Long> allocationIds);
}
