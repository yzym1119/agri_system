package com.agri_system.domain.purchase.service;

import com.agri_system.domain.purchase.model.PurchaseOrder;
import com.agri_system.domain.purchase.model.PurchaseOrderItem;
import com.agri_system.domain.purchase.repository.PurchaseItemRepository;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 采购领域服务
 * 负责采购订单的业务规则
 * 
 * @author AI Generated
 */
@RequiredArgsConstructor
public class PurchaseDomainService {
    
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository itemRepository;
    
    /**
     * 生成采购单号
     * 规则：PO + yyyyMMdd + 6位流水
     * 带并发安全重试：若生成的单号已存在则自动递增
     *
     * @return 采购单号
     */
    public String generateOrderNo() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PO" + datePrefix;

        // 查询当天最大流水号
        String maxOrderNo = purchaseRepository.findMaxOrderNoByPrefix(prefix);
        int sequence = 1;

        if (maxOrderNo != null && maxOrderNo.length() > prefix.length()) {
            try {
                String seqStr = maxOrderNo.substring(prefix.length());
                sequence = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                sequence = 1;
            }
        }

        // 并发安全：循环检查单号唯一性，最多重试10次
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = prefix + String.format("%06d", sequence);
            if (purchaseRepository.findByOrderNo(candidate).isEmpty()) {
                return candidate;
            }
            sequence++;
        }
        // 兜底：加上时间戳后缀保证唯一
        return prefix + String.format("%06d", sequence) + System.currentTimeMillis() % 1000;
    }
    
    /**
     * 计算采购订单总金额
     * 
     * @param items 明细列表
     * @return 总金额
     */
    public BigDecimal calculateTotalAmount(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(item -> {
                    if (item.getTotalPrice() != null) {
                        return item.getTotalPrice().setScale(2, java.math.RoundingMode.HALF_UP);
                    }
                    // 如果金额未计算，则计算
                    if (item.getQuantity() != null && item.getBuyPrice() != null) {
                        return item.getQuantity().multiply(item.getBuyPrice()).setScale(2, java.math.RoundingMode.HALF_UP);
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 检查是否可以审核
     * 
     * @param order 采购订单
     * @return 是否可以审核
     */
    public boolean checkCanAudit(PurchaseOrder order) {
        if (order == null) {
            return false;
        }
        if (!order.canAudit()) {
            return false;
        }
        // 检查是否有明细
        List<PurchaseOrderItem> items = itemRepository.findByOrderId(order.getId());
        if (items == null || items.isEmpty()) {
            return false;
        }
        // 检查明细是否合法
        for (PurchaseOrderItem item : items) {
            if (!item.isValidQuantity() || !item.isValidBuyPrice()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查是否可以提交（兼容旧方法名）
     * 
     * @param order 采购订单
     * @return 是否可以提交
     */
    @Deprecated
    public boolean checkCanSubmit(PurchaseOrder order) {
        return checkCanAudit(order);
    }
    
    /**
     * 检查是否可以入库
     * 
     * @param order 采购订单
     * @return 是否可以入库
     */
    public boolean checkCanInbound(PurchaseOrder order) {
        if (order == null) {
            return false;
        }
        return order.canInbound();
    }
}

