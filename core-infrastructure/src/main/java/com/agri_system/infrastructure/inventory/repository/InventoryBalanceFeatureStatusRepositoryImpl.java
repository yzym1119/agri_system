package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryBalanceFeatureStatus;
import com.agri_system.domain.inventory.repository.InventoryBalanceFeatureStatusRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryBalanceFeatureStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 库存模式切换状态仓储实现：按 (store_id, product_id) 自然键 upsert（唯一键是收敛点），
 * 激活与回滚走同一写路径（回滚 = save(LEGACY)，保留 cutover 留痕）。
 */
@Repository
@RequiredArgsConstructor
public class InventoryBalanceFeatureStatusRepositoryImpl implements InventoryBalanceFeatureStatusRepository {
    private final InventoryBalanceFeatureStatusMapper mapper;

    @Override
    public Optional<InventoryBalanceFeatureStatus> findByStoreAndProduct(Long storeId, Long productId) {
        return Optional.ofNullable(mapper.findByStoreAndProduct(storeId, productId));
    }

    @Override
    public InventoryBalanceFeatureStatus save(InventoryBalanceFeatureStatus status) {
        InventoryBalanceFeatureStatus existing = mapper.findByStoreAndProduct(status.getStoreId(), status.getProductId());
        return upsert(status, existing);
    }

    /**
     * 激活该门店 SKU 的 BATCH_LOCATION 模式；幂等，重复激活刷新 cutover 时间戳。
     */
    @Override
    public InventoryBalanceFeatureStatus enable(Long storeId, Long productId, Long operatorId) {
        if (storeId == null || productId == null || operatorId == null) {
            throw new IllegalArgumentException("storeId/productId/operatorId 不能为空");
        }
        InventoryBalanceFeatureStatus existing = mapper.findByStoreAndProduct(storeId, productId);
        InventoryBalanceFeatureStatus status = existing != null ? existing : new InventoryBalanceFeatureStatus();
        status.setStoreId(storeId);
        status.setProductId(productId);
        status.setBalanceMode(InventoryBalanceFeatureStatus.BATCH_LOCATION);
        status.setCutoverAt(LocalDateTime.now());
        status.setCutoverBy(operatorId);
        return upsert(status, existing);
    }

    /** 行存在则 update（id 取既有行、保留 createdAt、刷新 updatedAt；null 字段不参与 update，故回滚不清 cutover）。 */
    private InventoryBalanceFeatureStatus upsert(InventoryBalanceFeatureStatus status, InventoryBalanceFeatureStatus existing) {
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            status.setCreatedAt(now);
            status.setUpdatedAt(now);
            mapper.insert(status);
        } else {
            status.setId(existing.getId());
            status.setCreatedAt(existing.getCreatedAt());
            status.setUpdatedAt(now);
            mapper.update(status);
        }
        return status;
    }
}
