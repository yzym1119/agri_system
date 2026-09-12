package com.agri_system.infrastructure.purchase.repository;

import com.agri_system.domain.purchase.model.PurchaseOrder;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import com.agri_system.infrastructure.purchase.mapper.PurchaseMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 采购订单仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class PurchaseRepositoryImpl implements PurchaseRepository {
    
    private final PurchaseMapper purchaseMapper;
    
    @Override
    public void save(PurchaseOrder order) {
        if (order.getId() == null) {
            purchaseMapper.insert(order);
        } else {
            purchaseMapper.update(order);
        }
    }
    
    /**
     * 强制仅更新状态（用于取消等场景，避免部分字段未更新）
     */
    public void updateStatus(Long id, Integer status) {
        purchaseMapper.updateStatus(id, status);
    }
    
    @Override
    public Optional<PurchaseOrder> findById(Long id) {
        return Optional.ofNullable(purchaseMapper.selectOneById(id));
    }

    @Override
    public Optional<PurchaseOrder> findByIdForUpdate(Long id) {
        return Optional.ofNullable(purchaseMapper.findByIdForUpdate(id));
    }
    
    @Override
    public Optional<PurchaseOrder> findByOrderNo(String orderNo) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("purchase_no = ?", orderNo);
        return Optional.ofNullable(purchaseMapper.selectOneByQuery(queryWrapper));
    }
    
    @Override
    public Page<PurchaseOrder> findPage(Long storeId, Integer status, String orderNo,
                                        int pageNum, int pageSize) {
        return findPage(storeId, status, orderNo, null, null, null, pageNum, pageSize);
    }

    @Override
    public Page<PurchaseOrder> findPage(Long storeId, Integer status, String orderNo, String supplierName,
                                        java.time.LocalDateTime createdStartTime, java.time.LocalDateTime createdEndTime,
                                        int pageNum, int pageSize) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .from("purchase_order")
                .where("1 = 1");

        if (storeId != null) {
            queryWrapper.and("store_id = ?", storeId);
        }
        if (status != null) {
            queryWrapper.and("status = ?", status);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            queryWrapper.and("purchase_no LIKE ?", "%" + orderNo + "%");
        }
        if (supplierName != null && !supplierName.isEmpty()) {
            queryWrapper.leftJoin("supplier").on("purchase_order.supplier_id = supplier.id")
                    .and("supplier.name LIKE ?", "%" + supplierName + "%");
        }
        if (createdStartTime != null) {
            queryWrapper.and("created_at >= ?", createdStartTime);
        }
        if (createdEndTime != null) {
            queryWrapper.and("created_at <= ?", createdEndTime);
        }

        queryWrapper.orderBy("created_at DESC");

        return purchaseMapper.paginate(pageNum, pageSize, queryWrapper);
    }
    
    @Override
    public String findMaxOrderNoByPrefix(String prefix) {
        return purchaseMapper.findMaxOrderNoByPrefix(prefix);
    }

    @Override
    public long countBySupplierId(Long supplierId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("supplier_id = ?", supplierId);
        return purchaseMapper.selectCountByQuery(queryWrapper);
    }
}
