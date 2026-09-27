package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryDecisionLog;
import com.agri_system.domain.inventory.repository.InventoryDecisionLogRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryDecisionLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 库存风险人工决策留痕仓储实现（仅追加）。
 */
@Repository
@RequiredArgsConstructor
public class InventoryDecisionLogRepositoryImpl implements InventoryDecisionLogRepository {

    private final InventoryDecisionLogMapper mapper;

    @Override
    public InventoryDecisionLog save(InventoryDecisionLog decisionLog) {
        // 追加写：绝不 update/delete（表触发器同样禁止）
        mapper.insert(decisionLog);
        return decisionLog;
    }
}
