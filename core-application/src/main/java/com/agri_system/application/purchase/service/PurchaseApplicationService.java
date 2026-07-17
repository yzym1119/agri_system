package com.agri_system.application.purchase.service;

import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.purchase.dto.command.*;
import com.agri_system.application.purchase.dto.query.PurchasePageQuery;
import com.agri_system.application.purchase.dto.vo.PurchaseDetailVO;
import com.agri_system.application.purchase.dto.vo.PurchaseItemVO;
import com.agri_system.application.purchase.dto.vo.PurchasePageVO;
import com.agri_system.application.purchase.exception.PurchaseErrorCode;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.purchase.model.PurchaseOrder;
import com.agri_system.domain.purchase.model.PurchaseOrderItem;
import com.agri_system.domain.purchase.repository.PurchaseItemRepository;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import com.agri_system.domain.purchase.repository.SupplierRepository;
import com.agri_system.domain.purchase.service.PurchaseDomainService;
import com.agri_system.domain.store.repository.StoreRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 采购应用服务
 * 负责业务流程编排，不包含业务规则
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class PurchaseApplicationService {
    
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository itemRepository;
    private final PurchaseDomainService domainService;
    private final InventoryApplicationService inventoryService;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryBatchRepository batchRepository;
    
    /**
     * 创建采购单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseCreateCommand command, Long createdBy) {
        // 1. 校验门店是否存在
        storeRepository.findById(command.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException(
                        PurchaseErrorCode.PRODUCT_OR_STORE_NOT_FOUND + ": 门店不存在"));
        
        // 2. 校验商品是否存在
        for (PurchaseItemDTO item : command.getItems()) {
            productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            PurchaseErrorCode.PRODUCT_OR_STORE_NOT_FOUND + ": 商品不存在，ID=" + item.getProductId()));
        }
        
        // 3. 生成采购单号
        String orderNo = domainService.generateOrderNo();
        
        // 4. 创建采购订单
        PurchaseOrder order = new PurchaseOrder();
        order.setPurchaseNo(orderNo);
        order.setStoreId(command.getStoreId());
        order.setSupplierId(command.getSupplierId());
        order.setStatus(1); // 待提交
        order.setRemark(command.getRemark());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setCreatedBy(createdBy);
        
        // 5. 创建明细并计算金额
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrderItem> items = command.getItems().stream()
                .map(dto -> {
                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setProductId(dto.getProductId());
                    item.setQuantity(dto.getQty());
                    item.setUnit(dto.getUnit());
                    item.setBuyPrice(dto.getPrice());
                    item.setCreatedAt(now);
                    item.calculateTotalPrice();
                    return item;
                })
                .collect(Collectors.toList());
        
        // 6. 计算总金额
        BigDecimal totalAmount = domainService.calculateTotalAmount(items);
        order.setTotalAmount(totalAmount);
        
        // 7. 保存订单
        purchaseRepository.save(order);
        
        // 8. 保存明细
        items.forEach(item -> item.setPurchaseOrderId(order.getId()));
        itemRepository.saveBatch(items);
        
        return order.getId();
    }

    /**
     * 分页查询采购单
     */
    public Page<PurchasePageVO> page(PurchasePageQuery query) {
        Page<PurchaseOrder> page = purchaseRepository.findPage(
                query.getStoreId(),
                query.getStatus(),
                query.getOrderNo(),
                query.getSupplierName(),
                query.getCreatedStartTime(),
                query.getCreatedEndTime(),
                query.getPageNum(),
                query.getPageSize());

        Page<PurchasePageVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        List<PurchasePageVO> records = page.getRecords().stream().map(order -> {
            PurchasePageVO vo = new PurchasePageVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getPurchaseNo());
            vo.setStoreId(order.getStoreId());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setRemark(order.getRemark());
            vo.setCreatedAt(order.getCreatedAt());

            // store name
            storeRepository.findByIdIncludingDeleted(order.getStoreId()).ifPresent(store -> vo.setStoreName(store.getName()));
            // supplier name
            if (order.getSupplierId() != null) {
                supplierRepository.findById(order.getSupplierId()).ifPresent(supplier -> vo.setSupplierName(supplier.getName()));
            }
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(records);
        return voPage;
    }
    
    /**
     * 提交采购单
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(PurchaseSubmitCommand command) {
        // 1. 查询采购单
        PurchaseOrder order = purchaseRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND + ": 采购单不存在"));
        
        // 2. 检查是否可以审核/提交
        if (!domainService.checkCanAudit(order)) {
            throw new IllegalArgumentException(
                    PurchaseErrorCode.PURCHASE_ORDER_STATUS_INVALID + ": 采购单状态不允许提交");
        }
        
        // 3. 审核
        order.audit();
        order.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(order);
    }
    
    /**
     * 取消采购单
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(PurchaseCancelCommand command) {
        // 1. 查询采购单
        PurchaseOrder order = purchaseRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND + ": 采购单不存在"));
        
        // 2. 检查是否可以取消
        if (order.isInbound()) {
            throw new IllegalArgumentException(
                    PurchaseErrorCode.PURCHASE_ORDER_STATUS_INVALID + ": 已入库的采购单不能取消");
        }
        if (order.isCancelled()) {
            throw new IllegalArgumentException(
                    PurchaseErrorCode.PURCHASE_ORDER_STATUS_INVALID + ": 采购单已取消");
        }
        
        // 3. 取消
        order.cancel();
        order.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(order);
        // 再次强制更新状态，确保持久化
        purchaseRepository.updateStatus(order.getId(), order.getStatus());
    }
    
    /**
     * 采购入库
     * 必须开启事务，涉及：
     * - 更新采购单状态
     * - 写入库存批次
     * - 更新库存快照
     * - 更新商品平均成本
     */
    @Transactional(rollbackFor = Exception.class)
    public void inbound(PurchaseInboundCommand command, Long createdBy) {
        // 1. 查询采购单
        PurchaseOrder order = purchaseRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND + ": 采购单不存在"));
        
        // 2. 检查是否已入库
        if (order.isInbound()) {
            throw new IllegalArgumentException(
                    PurchaseErrorCode.PURCHASE_ORDER_ALREADY_INBOUND + ": 采购单已入库，禁止重复入库");
        }
        
        // 3. 检查是否可以入库
        if (!domainService.checkCanInbound(order)) {
            throw new IllegalArgumentException(
                    PurchaseErrorCode.PURCHASE_ORDER_STATUS_INVALID + ": 采购单状态不允许入库");
        }
        
        // 4. 查询明细
        List<PurchaseOrderItem> items = itemRepository.findByOrderId(order.getId());
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("采购单明细为空");
        }
        
        // 5. 处理入库明细：优先使用传入的 items，否则使用原采购明细
        boolean hasCustomItems = command.getItems() != null && !command.getItems().isEmpty();
        java.util.Map<Long, com.agri_system.application.purchase.dto.command.PurchaseInboundCommand.InboundItem> inboundMap =
                hasCustomItems ? command.getItems().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.agri_system.application.purchase.dto.command.PurchaseInboundCommand.InboundItem::getProductId,
                                i -> i)) : java.util.Collections.emptyMap();
        
        for (PurchaseOrderItem item : items) {
            // 5.1 校验商品并获取单位/换算
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            PurchaseErrorCode.PRODUCT_OR_STORE_NOT_FOUND + ": 商品不存在，ID=" + item.getProductId()));
            
            // 5.2 计算本次入库数量与成本价（允许与原采购单不一致）
            BigDecimal inboundQty = item.getQuantity();
            BigDecimal inboundCost = item.getBuyPrice();
            java.time.LocalDate inboundProductionDate = null;
            java.time.LocalDate inboundExpireDate = null;
            if (hasCustomItems) {
                com.agri_system.application.purchase.dto.command.PurchaseInboundCommand.InboundItem inboundItem =
                        inboundMap.get(item.getProductId());
                if (inboundItem == null) {
                    throw new IllegalArgumentException("入库明细缺少商品：" + item.getProductId());
                }
                inboundQty = inboundItem.getQuantity();
                inboundCost = inboundItem.getCostPrice();
                inboundProductionDate = inboundItem.getProductionDate();
                inboundExpireDate = inboundItem.getExpireDate();
            }

            // 5.2.1 如果采购单位为辅单位，换算为主单位后入库
            String itemUnit = item.getUnit();
            BigDecimal ratio = product.getMainToAuxRatio();
            if (itemUnit != null
                    && product.getAuxUnit() != null
                    && itemUnit.equals(product.getAuxUnit())
                    && ratio != null
                    && ratio.compareTo(BigDecimal.ZERO) > 0) {
                inboundQty = inboundQty.divide(ratio, 4, java.math.RoundingMode.HALF_UP);
                inboundCost = inboundCost.multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            
            // 5.3 调用库存模块入库
            InventoryInCommand inventoryCommand = new InventoryInCommand();
            inventoryCommand.setStoreId(order.getStoreId());
            inventoryCommand.setProductId(item.getProductId());
            inventoryCommand.setBatchNo(order.getPurchaseNo()); // 批次号 = 采购单号
            inventoryCommand.setQuantity(inboundQty);
            inventoryCommand.setCostPrice(inboundCost);
            inventoryCommand.setProductionDate(inboundProductionDate);
            inventoryCommand.setExpireDate(inboundExpireDate);
            inventoryCommand.setSourceType("PURCHASE");
            inventoryCommand.setSourceId(order.getId());
            inventoryCommand.setSourceLineNo(item.getId() != null ? item.getId().intValue() : null);
            
            inventoryService.inbound(inventoryCommand, createdBy);
            
            // 5.4 更新商品平均成本（使用实际入库数量与成本）
            updateProductAvgCostPrice(item.getProductId(), inboundQty, inboundCost);
        }
        
        // 6. 更新采购单状态为已入库
        order.completeInbound();
        order.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(order);
    }
    
    /**
     * 更新商品平均成本（移动加权平均）
     * 
     * 公式：
     * avg_cost_price_new = (old_avg_cost_price * old_total_qty + item.qty * item.price) / (old_total_qty + item.qty)
     * 
     * 边界情况：若 old_total_qty = 0，则 avg_cost_price_new = price
     */
    private void updateProductAvgCostPrice(Long productId, BigDecimal qty, BigDecimal price) {
        // 1. 查询商品当前平均成本
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        
        BigDecimal oldAvgCostPrice = product.getAvgCostPrice();
        if (oldAvgCostPrice == null) {
            oldAvgCostPrice = BigDecimal.ZERO;
        }
        
        // 2. 查询商品在全门店的总库存数量
        BigDecimal oldTotalQty = batchRepository.sumQuantityByProduct(productId);
        if (oldTotalQty == null) {
            oldTotalQty = BigDecimal.ZERO;
        }
        
        // 3. 计算新的平均成本
        BigDecimal newAvgCostPrice;
        if (oldTotalQty.compareTo(BigDecimal.ZERO) == 0) {
            // 边界情况：如果当前总库存为0，则新平均成本 = 本次进价
            newAvgCostPrice = price;
        } else {
            // 移动加权平均公式
            BigDecimal numerator = oldAvgCostPrice.multiply(oldTotalQty).add(qty.multiply(price));
            BigDecimal denominator = oldTotalQty.add(qty);
            newAvgCostPrice = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        }
        
        // 4. 更新商品平均成本
        product.setAvgCostPrice(newAvgCostPrice);
        productRepository.save(product);
    }
    
    /**
     * 查询采购单详情
     */
    public PurchaseDetailVO getDetail(Long orderId) {
        // 1. 查询采购单
        PurchaseOrder order = purchaseRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND + ": 采购单不存在"));
        
        // 2. 查询明细
        List<PurchaseOrderItem> items = itemRepository.findByOrderId(orderId);
        
        // 3. 查询商品信息（用于填充明细的商品名称）
        List<PurchaseItemVO> itemVOs = items.stream()
                .map(item -> {
                    PurchaseItemVO vo = new PurchaseItemVO();
                    vo.setId(item.getId());
                    vo.setProductId(item.getProductId());
                    vo.setQty(item.getQuantity());
                    vo.setPrice(item.getBuyPrice());
                    vo.setAmount(item.getTotalPrice());
                    vo.setUnit(item.getUnit());
                    
                    // 填充商品信息
                    productRepository.findById(item.getProductId()).ifPresent(product -> {
                        vo.setProductCode(product.getCode());
                        vo.setProductName(product.getName());
                        vo.setSpecification(product.getSpecification());
                        vo.setManufacturer(product.getManufacturer());
                    });
                    
                    return vo;
                })
                .collect(Collectors.toList());
        
        // 4. 查询门店信息（在构建VO时填充）
        
        // 5. 构建VO
        PurchaseDetailVO vo = new PurchaseDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getPurchaseNo());
        vo.setStoreId(order.getStoreId());
        vo.setSupplierId(order.getSupplierId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(getStatusDesc(order.getStatus()));
        vo.setRemark(order.getRemark());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());
        vo.setCreatedBy(order.getCreatedBy());
        vo.setItems(itemVOs);
        
        // 填充门店名称
        storeRepository.findByIdIncludingDeleted(order.getStoreId()).ifPresent(store -> {
            vo.setStoreName(store.getName());
        });
        
        // 填充供应商名称
        if (order.getSupplierId() != null) {
            supplierRepository.findById(order.getSupplierId()).ifPresent(supplier -> {
                vo.setSupplierName(supplier.getName());
            });
        }
        
        return vo;
    }
    
    /**
     * 获取状态描述
     */
    private String getStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 1:
                return "待提交";
            case 2:
                return "待入库";
            case 3:
                return "已入库";
            case 4:
                return "已取消";
            default:
                return "未知";
        }
    }
}
