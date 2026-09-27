package com.agri_system.domain.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryDecisionLog;

/**
 * 库存风险人工决策留痕仓储（仅追加）。
 *
 * <p>只暴露写入：事实表由数据库触发器保证不可更新/删除，仓储层不再提供 update/delete 方法。</p>
 */
public interface InventoryDecisionLogRepository {

    InventoryDecisionLog save(InventoryDecisionLog decisionLog);
}
