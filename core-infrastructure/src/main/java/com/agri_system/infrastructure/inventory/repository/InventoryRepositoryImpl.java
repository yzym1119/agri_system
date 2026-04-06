package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.Inventory;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.vo.InventoryPageVO;
import com.agri_system.infrastructure.inventory.mapper.InventoryMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 库存快照仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class InventoryRepositoryImpl implements InventoryRepository {
    
    private final InventoryMapper inventoryMapper;
    
    @Override
    public void save(Inventory inventory) {
        if (inventory.getId() == null) {
            inventoryMapper.insert(inventory);
        } else {
            inventoryMapper.update(inventory);
        }
    }
    
    @Override
    public Optional<Inventory> findByStoreAndProduct(Long storeId, Long productId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("store_id = ?", storeId)
                .where("product_id = ?", productId);
        return Optional.ofNullable(inventoryMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public void updateSnapshot(Long storeId, Long productId, BigDecimal quantity) {
        Optional<Inventory> inventoryOpt = findByStoreAndProduct(storeId, productId);
        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            inventory.setQuantity(quantity);
            inventoryMapper.update(inventory);
        } else {
            // 如果不存在，创建新记录
            createIfNotExists(storeId, productId, quantity);
        }
    }
    
    @Override
    public void createIfNotExists(Long storeId, Long productId, BigDecimal quantity) {
        Optional<Inventory> existing = findByStoreAndProduct(storeId, productId);
        if (existing.isEmpty()) {
            Inventory inventory = new Inventory();
            inventory.setStoreId(storeId);
            inventory.setProductId(productId);
            inventory.setQuantity(quantity);
            inventoryMapper.insert(inventory);
        }
    }

    @Override
    public Page<InventoryPageVO> findPage(Integer pageNum, Integer pageSize, Long storeId, String productName) {
        // 修复：从 product 表开始，LEFT JOIN inventory，确保无库存商品也可见
        QueryWrapper qw = QueryWrapper.create()
                .select("p.id AS productId",
                        "p.name AS productName",
                        "p.specification AS specification",
                        "p.manufacturer AS manufacturer",
                        storeId != null ? "COALESCE(i.store_id, " + storeId + ") AS storeId" : "COALESCE(i.store_id, 0) AS storeId",
                        "s.name AS storeName",
                        "COALESCE(i.quantity, 0) AS totalQuantity",
                        "p.main_unit AS unit",
                        "p.main_unit AS mainUnit",
                        "p.aux_unit AS auxUnit",
                        "p.main_to_aux_ratio AS mainToAuxRatio")
                .from("product").as("p");
        
        // LEFT JOIN inventory（如果指定了门店，在 ON 条件中过滤）
        if (storeId != null) {
            qw.leftJoin("inventory").as("i").on("i.product_id = p.id AND i.store_id = " + storeId);
        } else {
            qw.leftJoin("inventory").as("i").on("i.product_id = p.id");
        }
        
        qw.leftJoin("store").as("s").on("i.store_id = s.id");

        if (StringUtils.hasText(productName)) {
            qw.where("p.name LIKE ?", "%" + productName + "%");
        }

        qw.orderBy("p.name asc");

        Page<InventoryPageVO> page = new Page<>(pageNum, pageSize);
        return inventoryMapper.paginateAs(page, qw, InventoryPageVO.class);
    }
    
    @Override
    public java.util.Map<Long, java.math.BigDecimal> findInventoryByStoreAndProducts(Long storeId, java.util.List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        
        // 使用参数化查询，构建占位符
        StringBuilder inClause = new StringBuilder("(");
        Object[] params = new Object[productIds.size() + 1];
        params[0] = storeId;
        
        for (int i = 0; i < productIds.size(); i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append("?");
            params[i + 1] = productIds.get(i);
        }
        inClause.append(")");
        
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("product_id", "quantity")
                .from("inventory")
                .where("store_id = ? AND product_id IN " + inClause.toString(), params);
        
        List<Inventory> inventories = inventoryMapper.selectListByQuery(queryWrapper);
        
        java.util.Map<Long, java.math.BigDecimal> result = new java.util.HashMap<>();
        for (Inventory inventory : inventories) {
            result.put(inventory.getProductId(), inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO);
        }
        
        // 对于没有库存记录的商品，返回0
        for (Long productId : productIds) {
            result.putIfAbsent(productId, BigDecimal.ZERO);
        }
        
        return result;
    }
}









