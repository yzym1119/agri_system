package com.agri_system.application.purchase.service;

import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
import com.agri_system.application.purchase.dto.command.*;
import com.agri_system.application.purchase.dto.query.PurchasePageQuery;
import com.agri_system.application.purchase.dto.vo.PurchaseDetailVO;
import com.agri_system.application.purchase.dto.vo.PurchaseItemVO;
import com.agri_system.application.purchase.dto.vo.PurchasePageVO;
import com.agri_system.application.purchase.exception.PurchaseErrorCode;
import com.agri_system.domain.inventory.model.InventoryReceipt;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.model.InventoryReceiptLine;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptLineRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final SupplierRepository supplierRepository;
    private final ProductUnitService productUnitService;
    private final InventoryReceiptRepository receiptRepository;
    private final InventoryReceiptLineRepository receiptLineRepository;
    private final InventoryReceiptBatchRepository receiptBatchRepository;
    private final BatchBalanceService batchBalanceService;
    private final com.agri_system.domain.store.repository.StaffRepository staffRepository;
    
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
     * 旧入库接口只适配无歧义的单采购行、单实际批次；复杂收货必须使用 receive。
     */
    @Transactional(rollbackFor = Exception.class)
    public void inbound(PurchaseInboundCommand command, Long createdBy) {
        PurchaseOrder order = lockPurchaseOrder(command.getOrderId());
        checkCanReceive(order);
        List<PurchaseOrderItem> items = itemRepository.findByOrderId(order.getId());
        if (items == null || items.size() != 1) {
            throw new IllegalArgumentException("旧入库接口只支持一条采购明细，请使用采购收货接口");
        }
        PurchaseOrderItem item = items.get(0);
        PurchaseInboundCommand.InboundItem inboundItem = legacyInboundItem(command, item);
        ReceiptBatchCommand batch = new ReceiptBatchCommand();
        batch.setOriginalUnit(item.getUnit());
        batch.setOriginalQuantity(inboundItem == null ? item.getQuantity() : inboundItem.getQuantity());
        batch.setOriginalUnitCost(inboundItem == null ? item.getBuyPrice() : inboundItem.getCostPrice());
        batch.setProductionDate(inboundItem == null ? null : inboundItem.getProductionDate());
        batch.setExpiryDate(inboundItem == null ? null : inboundItem.getExpireDate());

        ReceivePurchaseCommand receiveCommand = new ReceivePurchaseCommand();
        receiveCommand.setRequestKey("PURCHASE-INBOUND:" + order.getId());
        receiveCommand.setPurchaseOrderId(order.getId());
        ReceivePurchaseCommand.ReceiptLineCommand line = new ReceivePurchaseCommand.ReceiptLineCommand();
        line.setPurchaseOrderItemId(item.getId());
        line.setProductId(item.getProductId());
        line.setBatches(List.of(batch));
        receiveCommand.setLines(List.of(line));
        receiveLocked(receiveCommand, createdBy, order, items);
    }

    /** 保存收货事实；余额服务只可在所有 header/line/batch 事实写入之后调用。 */
    @Transactional(rollbackFor = Exception.class)
    public Long receive(ReceivePurchaseCommand command, Long receiverId) {
        PurchaseOrder order = lockPurchaseOrder(command.getPurchaseOrderId());
        checkCanReceive(order);
        List<PurchaseOrderItem> orderItems = itemRepository.findByOrderId(order.getId());
        return receiveLocked(command, receiverId, order, orderItems);
    }

    public List<com.agri_system.application.purchase.dto.vo.ReceiptBatchLabelVO> receiptLabels(Long receiptId) {
        return receiptBatchRepository.findByReceiptId(receiptId).stream()
                .map(b -> new com.agri_system.application.purchase.dto.vo.ReceiptBatchLabelVO(b.getId(), b.getProductId(),
                        b.getInternalReceiptBatchNo(), b.getSupplierBatchNo(), b.getOriginalUnitName(), b.getOriginalQuantity(),
                        b.getProductionDate(), b.getExpiryDate(), b.getDateStatus())).toList();
    }

    /** 只读原事实；即使采购已收齐/取消也不能用当前采购状态否认历史成功。 */
    public com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO getReceiptSubmission(String requestKey,Long receiverId) {
        Long storeId=mobileStore(receiverId);
        if(requestKey==null||requestKey.isBlank()||requestKey.length()>64||!requestKey.equals(requestKey.trim())
                ||requestKey.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("收货请求号无效");
        var receipt=receiptRepository.findByRequestKeyAndOwner(requestKey,storeId,receiverId).orElse(null);
        if(receipt==null) return new com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO(requestKey,"UNKNOWN",storeId.toString(),null,null,null,List.of());
        if(!storeId.equals(receipt.getStoreId())||!receiverId.equals(receipt.getReceiverId())||!requestKey.equals(receipt.getRequestKey())
                ||!"RECEIVED".equals(receipt.getStatus())||receipt.getId()==null||receipt.getPurchaseOrderId()==null)
            throw new IllegalStateException("收货事实不完整，请联系门店核对");
        var originalLines=receiptLineRepository.findByReceiptId(receipt.getId());
        var originalBatches=receiptBatchRepository.findByReceiptId(receipt.getId());
        if(originalLines==null||originalLines.isEmpty()||originalBatches==null||originalBatches.isEmpty())
            throw new IllegalStateException("收货明细不完整，请联系门店核对");
        var seen=new HashSet<Long>();
        var resultLines=new ArrayList<com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO.Line>();
        for(var line:originalLines) {
            if(line.getId()==null||!seen.add(line.getId())||!receipt.getId().equals(line.getReceiptId())||!storeId.equals(line.getStoreId())
                    ||line.getProductId()==null||line.getPurchaseOrderItemId()==null) throw new IllegalStateException("收货行事实不一致");
            var lineBatches=originalBatches.stream().filter(b->line.getId().equals(b.getReceiptLineId())).map(b->{
                if(b.getId()==null||!storeId.equals(b.getStoreId())||!line.getProductId().equals(b.getProductId())
                        ||!hasText(b.getInternalReceiptBatchNo())||!hasText(b.getOriginalUnitName())||b.getOriginalQuantity()==null||b.getOriginalQuantity().signum()<=0
                        ||b.getOriginalUnitCost()==null||b.getOriginalUnitCost().signum()<=0) throw new IllegalStateException("收货批次事实不一致");
                return new com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO.Batch(b.getId().toString(),b.getInternalReceiptBatchNo(),b.getSupplierBatchNo(),
                        b.getOriginalUnitName(),decimalText(b.getOriginalQuantity()),decimalText(b.getOriginalUnitCost()),
                        b.getProductionDate()==null?null:b.getProductionDate().toString(),b.getExpiryDate()==null?null:b.getExpiryDate().toString(),b.getDateStatus(),b.getInspectionReference());
            }).toList();
            if(lineBatches.isEmpty()) throw new IllegalStateException("收货行缺少批次事实");
            resultLines.add(new com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO.Line(line.getPurchaseOrderItemId().toString(),line.getProductId().toString(),line.getRemark(),lineBatches));
        }
        if(resultLines.stream().mapToInt(l->l.batches().size()).sum()!=originalBatches.size()) throw new IllegalStateException("收货批次归属不完整");
        return new com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO(requestKey,"COMPLETED",storeId.toString(),receipt.getPurchaseOrderId().toString(),receipt.getId().toString(),receipt.getReceiptNo(),List.copyOf(resultLines));
    }

    private Long mobileStore(Long receiverId) {
        var staff=receiverId==null?null:staffRepository.findIdentityById(receiverId).orElse(null);
        if(staff==null||!staff.isActive()||staff.hasBeenDeleted()) throw new IllegalArgumentException("需要当前有效员工身份");
        Long store=staff.getStoreId();
        if(store==null||store<=0) throw new IllegalArgumentException("当前员工未分配门店");
        return store;
    }
    private static String decimalText(BigDecimal value) {return value.stripTrailingZeros().toPlainString();}

    public com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Detail mobileReceivingDetail(Long orderId,Long receiverId) {
        Long store=mobileStore(receiverId);
        var detail=receivingDetail(orderId);
        if(!store.equals(detail.getStoreId())) throw new IllegalArgumentException("采购单不存在或不在本店");
        var result=new ArrayList<com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Item>();
        for(var item:detail.getItems()) {
            var units=productUnitService.getUnits(item.getProductId()).stream().filter(ProductUnit::isEnabled).filter(u->u.getId()!=null).toList();
            ProductUnit base=units.stream().filter(ProductUnit::isBase).findFirst().orElseThrow(()->new IllegalArgumentException("商品缺少已确认的基准单位"));
            ProductUnit ordered=units.stream().filter(u->item.getUnit().equals(u.getUnitName())).findFirst().orElseThrow(()->new IllegalArgumentException("采购单位不可用"));
            BigDecimal ratio=ordered.getBaseQuantityPerUnit();
            if(ratio==null||ratio.signum()<=0||!Integer.valueOf(1).equals(ordered.getAllowPurchase())) throw new IllegalArgumentException("采购单位资料待核对");
            BigDecimal received=zero(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(item.getId()));
            BigDecimal original=item.getQty();
            BigDecimal remainingBase=original.multiply(ratio).subtract(received).max(BigDecimal.ZERO);
            var purchasable=units.stream().filter(u->Integer.valueOf(1).equals(u.getAllowPurchase())).map(u->
                    new com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Unit(u.getId().toString(),u.getUnitName(),decimalText(u.getBaseQuantityPerUnit()))).toList();
            var codes=new ArrayList<String>();codes.add(item.getProductCode());codes.add(item.getProductBarcode());units.forEach(u->codes.add(u.getBarcode()));
            var scanCodes=codes.stream().filter(this::hasText).distinct().toList();
            result.add(new com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Item(item.getId().toString(),item.getProductId().toString(),item.getProductCode(),item.getProductName(),
                    decimalText(original),decimalText(received),decimalText(remainingBase),item.getUnit(),base.getUnitName(),
                    com.agri_system.domain.product.service.QuantityGranularityPolicy.allowsFractionalBaseQuantity(base.getUnitName()),purchasable,scanCodes));
        }
        return new com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Detail(detail.getId().toString(),store.toString(),detail.getOrderNo(),detail.getSupplierName(),List.copyOf(result));
    }

    public com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Orders mobileReceivingPage(int page,String orderNo,Long receiverId) {
        Long store=mobileStore(receiverId);
        if(page<1||page>10000||orderNo==null||orderNo.length()>100) throw new IllegalArgumentException("页码或采购单号无效");
        var query=new PurchasePageQuery();query.setPageNum(page);query.setPageSize(20);query.setStoreId(store);query.setStatus(2);query.setOrderNo(orderNo.trim());
        var result=receivingPage(query);
        var orders=result.getRecords().stream().map(order->{
            if(!store.equals(order.getStoreId())) throw new IllegalStateException("采购门店资料待核对");
            return new com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Order(order.getId().toString(),order.getOrderNo(),order.getSupplierName());
        }).toList();
        return new com.agri_system.application.purchase.dto.vo.MobileReceivingVO.Orders(store.toString(),page,result.getTotalRow()>(long)page*20,orders);
    }

    /** 调用方必须先持有 purchase_order 的行锁，避免 legacy adapter 二次锁定。 */
    private Long receiveLocked(ReceivePurchaseCommand command, Long receiverId,
                               PurchaseOrder order, List<PurchaseOrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("采购单明细为空");
        }
        if (command.getLines() == null || command.getLines().isEmpty()) {
            throw new IllegalArgumentException("收货行不能为空");
        }

        Set<Long> referencedPurchaseItemIds = new HashSet<>();
        List<PreparedReceiptLine> preparedLines = new ArrayList<>();
        boolean overReceipt = false;
        for (ReceivePurchaseCommand.ReceiptLineCommand lineCommand : command.getLines()) {
            if (lineCommand == null || !referencedPurchaseItemIds.add(lineCommand.getPurchaseOrderItemId())) {
                throw new IllegalArgumentException("同一采购明细只能对应一条收货行");
            }
            PurchaseOrderItem purchaseItem = findPurchaseItem(orderItems, lineCommand.getPurchaseOrderItemId());
            if (!purchaseItem.getProductId().equals(lineCommand.getProductId())) {
                throw new IllegalArgumentException("收货商品与采购明细SKU不一致");
            }
            Product product = productRepository.findById(lineCommand.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            PurchaseErrorCode.PRODUCT_OR_STORE_NOT_FOUND + ": 商品不存在，ID=" + lineCommand.getProductId()));
            requirePurchasableUnit(product.getId(), purchaseItem.getUnit());
            ProductUnit baseUnit = requireBaseUnit(product.getId());
            UnitConversionSnapshot expected = productUnitService.toBaseQuantity(
                    product.getId(), purchaseItem.getUnit(), purchaseItem.getQuantity());
            PreparedReceiptLine preparedLine = new PreparedReceiptLine(lineCommand, purchaseItem, baseUnit);
            if (lineCommand.getBatches() == null || lineCommand.getBatches().isEmpty()) {
                throw new IllegalArgumentException("收货批次不能为空");
            }
            for (ReceiptBatchCommand batchCommand : lineCommand.getBatches()) {
                PreparedReceiptBatch preparedBatch = prepareBatch(product.getId(), baseUnit, batchCommand);
                preparedLine.batches.add(preparedBatch);
            }
            BigDecimal previouslyReceived = zero(receiptBatchRepository
                    .sumBaseQuantityByPurchaseOrderItemId(purchaseItem.getId()));
            if (previouslyReceived.add(preparedLine.totalBaseQuantity()).compareTo(expected.baseQuantity()) > 0) {
                overReceipt = true;
            }
            preparedLines.add(preparedLine);
        }
        if (overReceipt && !hasText(command.getExceptionReason())) {
            throw new IllegalArgumentException("累计收货超过采购数量，必须填写exceptionReason");
        }

        if (!hasText(command.getRequestKey())) throw new IllegalArgumentException("收货请求标识不能为空");
        preparedLines.stream().map(l -> l.purchaseItem.getProductId()).distinct().sorted()
                .forEach(id -> batchBalanceService.lockStock(order.getStoreId(), id));
        LocalDateTime now = LocalDateTime.now();
        InventoryReceipt receipt = new InventoryReceipt();
        receipt.setRequestKey(command.getRequestKey());
        receipt.setReceiptNo(newReceiptNo(now));
        receipt.setPurchaseOrderId(order.getId());
        receipt.setSupplierId(order.getSupplierId());
        receipt.setStoreId(order.getStoreId());
        receipt.setStatus("RECEIVED");
        receipt.setReceivedAt(now);
        receipt.setReceiverId(receiverId);
        receipt.setRemark(overReceipt ? appendAuditReason(command.getRemark(), command.getExceptionReason()) : command.getRemark());
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);
        receiptRepository.save(receipt);

        List<InventoryReceiptBatch> persistedBatches = new ArrayList<>();
        int lineNo = 1;
        int batchNo = 1;
        for (PreparedReceiptLine preparedLine : preparedLines) {
            InventoryReceiptLine receiptLine = new InventoryReceiptLine();
            receiptLine.setReceiptId(receipt.getId());
            receiptLine.setLineNo(lineNo++);
            receiptLine.setPurchaseOrderItemId(preparedLine.purchaseItem.getId());
            receiptLine.setProductId(preparedLine.purchaseItem.getProductId());
            receiptLine.setStoreId(order.getStoreId());
            receiptLine.setRemark(preparedLine.command.getRemark());
            receiptLine.setCreatedAt(now);
            receiptLineRepository.save(receiptLine);
            for (PreparedReceiptBatch preparedBatch : preparedLine.batches) {
                InventoryReceiptBatch receiptBatch = new InventoryReceiptBatch();
                receiptBatch.setReceiptLineId(receiptLine.getId());
                receiptBatch.setProductId(preparedLine.purchaseItem.getProductId());
                receiptBatch.setStoreId(order.getStoreId());
                receiptBatch.setSupplierBatchNo(preparedBatch.command.getSupplierBatchNo());
                receiptBatch.setInternalReceiptBatchNo("INTERNAL-RECEIPT-" + receipt.getReceiptNo() + "-" + batchNo++);
                receiptBatch.setOriginalUnitId(preparedBatch.originalUnit.getId());
                receiptBatch.setOriginalUnitName(preparedBatch.originalUnit.getUnitName());
                receiptBatch.setOriginalQuantity(preparedBatch.command.getOriginalQuantity().setScale(8, RoundingMode.HALF_UP));
                receiptBatch.setOriginalUnitCost(preparedBatch.command.getOriginalUnitCost().setScale(8, RoundingMode.HALF_UP));
                receiptBatch.setBaseUnitId(preparedLine.baseUnit.getId());
                receiptBatch.setBaseQuantity(preparedBatch.baseQuantity);
                receiptBatch.setBaseUnitCost(preparedBatch.baseUnitCost);
                receiptBatch.setDateStatus(preparedBatch.dateStatus);
                receiptBatch.setProductionDate(preparedBatch.command.getProductionDate());
                receiptBatch.setExpiryDate(preparedBatch.command.getExpiryDate());
                receiptBatch.setInspectionReference(preparedBatch.command.getInspectionReference());
                receiptBatch.setReceiverId(receiverId);
                receiptBatch.setReceivedAt(now);
                receiptBatch.setCreatedAt(now);
                receiptBatchRepository.save(receiptBatch);
                persistedBatches.add(receiptBatch);
            }
        }

        for (InventoryReceiptBatch persistedBatch : persistedBatches) {
            batchBalanceService.increaseFromReceipt(receipt.getId(), persistedBatch);
        }
        if (isOrderFullyReceived(orderItems)) {
            order.completeInbound();
            order.setUpdatedAt(now);
            purchaseRepository.save(order);
        }
        return receipt.getId();
    }

    private PurchaseOrder lockPurchaseOrder(Long purchaseOrderId) {
        // 收货事务以采购单行锁为边界，后续状态和累计读取均受该锁串行化保护。
        return purchaseRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND + ": 采购单不存在"));
    }

    private void checkCanReceive(PurchaseOrder order) {
        if (order.isInbound()) {
            throw new IllegalArgumentException(PurchaseErrorCode.PURCHASE_ORDER_ALREADY_INBOUND + ": 采购单已全部收货");
        }
        if (!domainService.checkCanInbound(order)) {
            throw new IllegalArgumentException(PurchaseErrorCode.PURCHASE_ORDER_STATUS_INVALID + ": 采购单状态不允许收货");
        }
    }

    private PurchaseInboundCommand.InboundItem legacyInboundItem(PurchaseInboundCommand command, PurchaseOrderItem item) {
        if (command.getItems() == null || command.getItems().isEmpty()) {
            return null;
        }
        if (command.getItems().size() != 1 || !item.getProductId().equals(command.getItems().get(0).getProductId())) {
            throw new IllegalArgumentException("旧入库接口的实际明细不唯一，请使用采购收货接口");
        }
        return command.getItems().get(0);
    }

    private PurchaseOrderItem findPurchaseItem(List<PurchaseOrderItem> items, Long purchaseOrderItemId) {
        return items.stream().filter(item -> item.getId().equals(purchaseOrderItemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("采购明细不属于当前采购单: " + purchaseOrderItemId));
    }

    private ProductUnit requirePurchasableUnit(Long productId, String unit) {
        ProductUnit productUnit = productUnitService.requireEnabledUnit(productId, unit, ProductUnitService.UnitOperation.PURCHASE);
        if (productUnit.getId() == null) {
            throw new IllegalArgumentException("SKU单位尚未确认，不能写入收货事实");
        }
        return productUnit;
    }

    private ProductUnit requireBaseUnit(Long productId) {
        return productUnitService.getUnits(productId).stream()
                .filter(ProductUnit::isBase)
                .filter(unit -> unit.getId() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("SKU缺少已确认的基准单位，不能写入收货事实"));
    }

    private PreparedReceiptBatch prepareBatch(Long productId, ProductUnit baseUnit, ReceiptBatchCommand batchCommand) {
        if (batchCommand == null || !hasText(batchCommand.getOriginalUnit())
                || batchCommand.getOriginalQuantity() == null || batchCommand.getOriginalQuantity().compareTo(BigDecimal.ZERO) <= 0
                || batchCommand.getOriginalUnitCost() == null || batchCommand.getOriginalUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("收货批次的单位、数量和单价必须完整且大于0");
        }
        requireReceiptDecimal(batchCommand.getOriginalQuantity(),"原始数量");
        requireReceiptDecimal(batchCommand.getOriginalUnitCost(),"原始单位进价");
        boolean hasProductionDate = batchCommand.getProductionDate() != null;
        boolean hasExpiryDate = batchCommand.getExpiryDate() != null;
        if (hasProductionDate && hasExpiryDate && batchCommand.getProductionDate().isAfter(batchCommand.getExpiryDate())) {
            throw new IllegalArgumentException("生产日期不能晚于有效期");
        }
        ProductUnit originalUnit = requirePurchasableUnit(productId, batchCommand.getOriginalUnit());
        UnitConversionSnapshot conversion = productUnitService.toBaseQuantity(
                productId, batchCommand.getOriginalUnit(), batchCommand.getOriginalQuantity());
        BigDecimal baseUnitCost = batchCommand.getOriginalUnitCost()
                .divide(conversion.baseQuantityPerUnit(), 8, RoundingMode.HALF_UP);
        return new PreparedReceiptBatch(batchCommand, originalUnit, conversion.baseQuantity(), baseUnitCost,
                hasProductionDate && hasExpiryDate ? "KNOWN" : hasProductionDate || hasExpiryDate ? "PARTIAL" : "UNKNOWN");
    }

    private static void requireReceiptDecimal(BigDecimal value,String label) {
        try {
            if(value.setScale(8,RoundingMode.UNNECESSARY).precision()>20) throw new ArithmeticException();
        } catch(ArithmeticException error) {throw new IllegalArgumentException(label+"超出12位整数或8位小数，不能舍入",error);}
    }

    private boolean isOrderFullyReceived(List<PurchaseOrderItem> orderItems) {
        for (PurchaseOrderItem item : orderItems) {
            UnitConversionSnapshot expected = productUnitService.toBaseQuantity(item.getProductId(), item.getUnit(), item.getQuantity());
            BigDecimal received = zero(receiptBatchRepository.sumBaseQuantityByPurchaseOrderItemId(item.getId()));
            if (received.compareTo(expected.baseQuantity()) < 0) {
                return false;
            }
        }
        return true;
    }

    private String newReceiptNo(LocalDateTime now) {
        return "RCV-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String appendAuditReason(String remark, String exceptionReason) {
        String audit = "OVER_RECEIPT: " + exceptionReason.trim();
        return hasText(remark) ? remark.trim() + " | " + audit : audit;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class PreparedReceiptLine {
        private final ReceivePurchaseCommand.ReceiptLineCommand command;
        private final PurchaseOrderItem purchaseItem;
        private final ProductUnit baseUnit;
        private final List<PreparedReceiptBatch> batches = new ArrayList<>();

        private PreparedReceiptLine(ReceivePurchaseCommand.ReceiptLineCommand command, PurchaseOrderItem purchaseItem,
                                    ProductUnit baseUnit) {
            this.command = command;
            this.purchaseItem = purchaseItem;
            this.baseUnit = baseUnit;
        }

        private BigDecimal totalBaseQuantity() {
            return batches.stream().map(batch -> batch.baseQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static final class PreparedReceiptBatch {
        private final ReceiptBatchCommand command;
        private final ProductUnit originalUnit;
        private final BigDecimal baseQuantity;
        private final BigDecimal baseUnitCost;
        private final String dateStatus;

        private PreparedReceiptBatch(ReceiptBatchCommand command, ProductUnit originalUnit, BigDecimal baseQuantity,
                                     BigDecimal baseUnitCost, String dateStatus) {
            this.command = command;
            this.originalUnit = originalUnit;
            this.baseQuantity = baseQuantity;
            this.baseUnitCost = baseUnitCost;
            this.dateStatus = dateStatus;
        }
    }

    /**
     * 查询采购单详情
     */
    public Page<PurchasePageVO> receivingPage(PurchasePageQuery query) {
        query.setStatus(2);
        return page(query);
    }

    public PurchaseDetailVO receivingDetail(Long orderId) {
        var detail=getDetail(orderId);
        if (!Integer.valueOf(2).equals(detail.getStatus())) throw new IllegalArgumentException("采购单当前不在待收货状态");
        return detail;
    }

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
                        vo.setProductBarcode(product.getBarcode());
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
