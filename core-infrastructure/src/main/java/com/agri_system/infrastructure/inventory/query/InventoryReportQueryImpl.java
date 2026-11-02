package com.agri_system.infrastructure.inventory.query;

import com.agri_system.domain.inventory.query.InventoryReportBatch;
import com.agri_system.domain.inventory.query.InventoryReportQuery;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;

/** 复用现有统一批次余额SQL与Flex连接，不查询旧库存表，不提供保存或锁库存入口。 */
@Repository
@RequiredArgsConstructor
public class InventoryReportQueryImpl implements InventoryReportQuery {
    private final InventoryBatchMapper mapper;
    @Override public List<InventoryReportBatch> findCurrentBatches(Long storeId) {
        return mapper.currentBalances(storeId).stream().map(batch -> new InventoryReportBatch(
                batch.getId(),batch.getStoreId(),batch.getProductId(),batch.getBatchNo(),
                batch.getQuantity(),batch.getCostPrice(),batch.getProductionDate(),batch.getExpireDate(),batch.getCreatedAt())).toList();
    }
}
