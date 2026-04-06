package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryTransaction;
import com.agri_system.domain.inventory.repository.InventoryTransactionRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryTransactionMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 库存流水仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class InventoryTransactionRepositoryImpl implements InventoryTransactionRepository {
    
    private final InventoryTransactionMapper transactionMapper;
    
    @Override
    public void save(InventoryTransaction transaction) {
        if (transaction.getId() == null) {
            transactionMapper.insert(transaction);
        } else {
            transactionMapper.update(transaction);
        }
    }
    
    @Override
    public Page<InventoryTransaction> findPage(Integer pageNum, Integer pageSize, 
                                               Long storeId, String productName, 
                                               Integer type, String startTime, String endTime) {
        QueryWrapper qw = QueryWrapper.create()
                .select("t.*")
                .from("inventory_transaction").as("t")
                .leftJoin("product").as("p").on("t.product_id = p.id")
                .leftJoin("store").as("s").on("t.store_id = s.id");
        
        if (storeId != null) {
            qw.where("t.store_id = ?", storeId);
        }
        if (StringUtils.hasText(productName)) {
            qw.and("p.name LIKE ?", "%" + productName + "%");
        }
        if (type != null) {
            qw.and("t.transaction_type = ?", type);
        }
        if (StringUtils.hasText(startTime)) {
            qw.and("t.created_at >= ?", LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (StringUtils.hasText(endTime)) {
            qw.and("t.created_at <= ?", LocalDateTime.parse(endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        qw.orderBy("t.created_at DESC");
        
        Page<InventoryTransaction> page = new Page<>(pageNum, pageSize);
        return transactionMapper.paginate(page, qw);
    }
}









