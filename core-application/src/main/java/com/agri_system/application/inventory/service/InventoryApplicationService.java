package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.command.InventoryTransferCommand;
import com.agri_system.application.inventory.dto.query.InventoryQuery;
import com.agri_system.application.inventory.dto.query.InventoryTransactionQuery;
import com.agri_system.application.inventory.dto.vo.BatchDetailVO;
import com.agri_system.application.inventory.dto.vo.InventoryDetailVO;
import com.agri_system.application.inventory.dto.vo.InventoryTransactionVO;
import com.agri_system.application.inventory.exception.InventoryErrorCode;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.inventory.enums.InventoryTransactionType;
import com.agri_system.domain.inventory.model.Inventory;
import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.inventory.model.InventoryTransaction;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryTransactionRepository;
import com.agri_system.domain.inventory.service.InventoryDomainService;
import com.agri_system.domain.inventory.vo.InventoryPageVO;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存应用服务
 * 负责业务流程编排，不包含业务规则
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class InventoryApplicationService {
    
    private final InventoryDomainService domainService;
    private final InventoryBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;
    private final PurchaseRepository purchaseRepository;
    
    /**
     * 入库
     * 
     * @param command 入库命令
     * @param createdBy 操作人ID
     * @return 批次ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(InventoryInCommand command, Long createdBy) {
        // 校验商品是否存在并获取单位
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        // 获取单位（优先使用主单位）
        String unit = product.getMainUnit();
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("商品单位信息不完整");
        }
        
        // 调用领域服务执行入库
        return domainService.inbound(
                command.getStoreId(),
                command.getProductId(),
                command.getBatchNo(),
                command.getQuantity(),
                command.getCostPrice(),
                command.getProductionDate(),
                command.getExpireDate(),
                command.getSourceType(),
                command.getSourceId(),
                command.getSourceLineNo(),
                unit,
                createdBy
        );
    }
    
    /**
     * 出库（FIFO）
     * 
     * @param command 出库命令
     * @param createdBy 操作人ID
     * @return 扣减的批次详情列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BatchDetailVO> outbound(InventoryOutCommand command, Long createdBy) {
        // 1. 校验商品是否存在并获取单位信息
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        String mainUnit = product.getMainUnit();
        String auxUnit = product.getAuxUnit();
        BigDecimal mainToAuxRatio = product.getMainToAuxRatio();
        
        if (mainUnit == null || mainUnit.trim().isEmpty()) {
            throw new IllegalArgumentException("商品主单位信息不完整");
        }
        
        // 2. 单位归一化：将业务单位转换为库存主单位
        BigDecimal deductMainQty; // 最终要扣减的主单位数量
        String originalUnit = command.getUnit(); // 原始单位（可能为空，表示使用主单位）
        BigDecimal originalQuantity = command.getQuantity(); // 原始数量
        
        if (originalUnit != null && !originalUnit.trim().isEmpty() 
                && auxUnit != null && auxUnit.equals(originalUnit) 
                && mainToAuxRatio != null && mainToAuxRatio.compareTo(BigDecimal.ONE) > 0) {
            // 场景：按辅单位销售（如卖 1 瓶，1箱=3瓶）
            // 换算公式：主单位数量 = 辅单位数量 / ratio
            deductMainQty = originalQuantity.divide(mainToAuxRatio, 8, RoundingMode.HALF_UP);
        } else {
            // 场景：按主单位销售，或无换算 -> 直接使用
            deductMainQty = originalQuantity;
            // 如果未指定单位，默认使用主单位
            if (originalUnit == null || originalUnit.trim().isEmpty()) {
                originalUnit = mainUnit;
            }
        }
        
        // 3. 确定流水类型（transactionType）
        // 如果 referenceType 是 "ORDER"，根据订单类型确定 transactionType
        Integer transactionType = InventoryTransactionType.POS_SALES.getCode(); // 默认 POS 销售
        if ("ORDER".equalsIgnoreCase(command.getReferenceType()) && command.getReferenceId() != null) {
            // 查询订单以确定类型
            var orderOpt = orderRepository.findById(command.getReferenceId());
            if (orderOpt.isPresent()) {
                var order = orderOpt.get();
                // orderType: 1-线下POS，2-小程序自提，3-小程序配送
                // transactionType: 2-POS_SALES, 3-ONLINE_SALES
                if (order.getOrderType() != null && order.getOrderType() == 1) {
                    transactionType = InventoryTransactionType.POS_SALES.getCode();
                } else if (order.getOrderType() != null && (order.getOrderType() == 2 || order.getOrderType() == 3)) {
                    transactionType = InventoryTransactionType.ONLINE_SALES.getCode();
                }
            }
        }
        
        // 4. 调用领域服务执行出库（传入归一化的主单位数量）
        List<InventoryDomainService.BatchDeductionDetail> deductionDetails = 
                domainService.outbound(
                        command.getStoreId(),
                        command.getProductId(),
                        deductMainQty, // 归一化的主单位数量
                        mainUnit, // 主单位（用于流水记录）
                        originalUnit, // 原始单位（用于审计）
                        originalQuantity, // 原始数量（用于审计）
                        createdBy,
                        transactionType,
                        command.getReferenceType(),
                        command.getReferenceId()
                );
        
        // 5. 转换为VO
        return deductionDetails.stream()
                .map(detail -> {
                    BatchDetailVO vo = new BatchDetailVO();
                    vo.setId(detail.getBatchId());
                    vo.setBatchNo(detail.getBatchNo());
                    vo.setExpireDate(detail.getExpireDate());
                    vo.setCostPrice(detail.getCostPrice());
                    vo.setQuantity(detail.getQuantity());
                    return vo;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 调拨
     * 
     * @param command 调拨命令
     * @param createdBy 操作人ID
     * @return 调拨详情
     */
    @Transactional(rollbackFor = Exception.class)
    public InventoryDomainService.TransferDetail transfer(InventoryTransferCommand command, Long createdBy) {
        // 校验商品是否存在并获取单位
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        // 获取单位（优先使用主单位）
        String unit = product.getMainUnit();
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("商品单位信息不完整");
        }
        
        // 调用领域服务执行调拨
        // 注意：这里需要生成调拨单ID，暂时使用临时ID
        Long transferId = System.currentTimeMillis(); // TODO: 应该从调拨单模块获取
        
        return domainService.transfer(
                command.getFromStoreId(),
                command.getToStoreId(),
                command.getProductId(),
                command.getQuantity(),
                unit,
                createdBy,
                transferId
        );
    }
    
    /**
     * 查询库存详情（包含批次列表）
     * 
     * @param storeId 门店ID
     * @param productId 商品ID
     * @return 库存详情
     */
    public InventoryDetailVO getInventoryDetail(Long storeId, Long productId) {
        // 查询库存快照
        Inventory inventory = inventoryRepository.findByStoreAndProduct(storeId, productId)
                .orElseGet(() -> {
                    // 如果快照不存在，创建并同步
                    domainService.refreshTotalStock(storeId, productId);
                    return inventoryRepository.findByStoreAndProduct(storeId, productId)
                            .orElse(new Inventory());
                });
        
        // 查询批次列表
        List<InventoryBatch> batches = batchRepository.findActiveBatches(storeId, productId);
        
        // 查询门店和商品信息
        Store store = storeRepository.findById(storeId).orElse(null);
        Product product = productRepository.findById(productId).orElse(null);
        
        // 转换为VO
        InventoryDetailVO vo = new InventoryDetailVO();
        vo.setId(inventory.getId());
        vo.setStoreId(storeId);
        vo.setProductId(productId);
        vo.setQuantity(inventory.getQuantity());
        if (store != null) {
            vo.setStoreName(store.getName());
        }
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setUnit(product.getMainUnit());
        }
        vo.setBatches(batches.stream()
                .map(this::convertToBatchVO)
                .collect(Collectors.toList()));
        
        return vo;
    }

    /**
     * 分页查询库存列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public Page<InventoryPageVO> queryInventoryPage(InventoryQuery query) {
        return inventoryRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getStoreId(),
                query.getProductName());
    }
    
    /**
     * 查询批次详情
     * 
     * @param batchId 批次ID
     * @return 批次详情
     */
    public BatchDetailVO getBatchDetail(Long batchId) {
        InventoryBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在"));
        
        return convertToBatchVO(batch);
    }
    
    /**
     * 库存调整（盘点）
     * 
     * @param command 调整命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(InventoryAdjustCommand command, Long createdBy) {
        // 校验商品是否存在并获取单位
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        // 获取单位（优先使用主单位）
        String unit = product.getMainUnit();
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("商品单位信息不完整");
        }
        
        // 确定流水类型：1-盘盈，2-盘亏
        Integer transactionType = command.getType() == 1 
                ? InventoryTransactionType.ADJUSTMENT_IN.getCode()
                : InventoryTransactionType.ADJUSTMENT_OUT.getCode();
        
        // 调用领域服务执行调整
        domainService.adjust(
                command.getStoreId(),
                command.getProductId(),
                command.getQuantityDiff(),
                transactionType,
                unit,
                createdBy,
                command.getRemark()
        );
    }
    
    /**
     * 分页查询库存流水
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    public Page<InventoryTransactionVO> queryTransactionPage(InventoryTransactionQuery query) {
        Page<InventoryTransaction> page = transactionRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getStoreId(),
                query.getProductName(),
                query.getType(),
                query.getStartTime(),
                query.getEndTime()
        );
        
        // 转换为VO
        List<InventoryTransactionVO> voList = page.getRecords().stream()
                .map(this::convertToTransactionVO)
                .collect(Collectors.toList());
        
        Page<InventoryTransactionVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    // ==================== 私有方法：转换 ====================
    
    private BatchDetailVO convertToBatchVO(InventoryBatch batch) {
        BatchDetailVO vo = new BatchDetailVO();
        vo.setId(batch.getId());
        vo.setStoreId(batch.getStoreId());
        vo.setProductId(batch.getProductId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setProductionDate(batch.getProductionDate());
        vo.setExpireDate(batch.getExpireDate());
        vo.setQuantity(batch.getQuantity());
        vo.setCostPrice(batch.getCostPrice());
        vo.setIsActive(batch.getIsActive());
        vo.setCreatedAt(batch.getCreatedAt());
        vo.setUpdatedAt(batch.getUpdatedAt());
        vo.setIsExpired(batch.isExpired());
        vo.setIsExpiringSoon(batch.isExpiringSoon());
        return vo;
    }
    
    private InventoryTransactionVO convertToTransactionVO(InventoryTransaction transaction) {
        InventoryTransactionVO vo = new InventoryTransactionVO();
        vo.setId(transaction.getId());
        vo.setStoreId(transaction.getStoreId());
        vo.setProductId(transaction.getProductId());
        vo.setQuantity(transaction.getQuantity());
        vo.setAfterQuantity(transaction.getAfterQuantity());
        vo.setType(transaction.getTransactionType());
        vo.setCreatedBy(transaction.getCreatedBy());
        vo.setCreatedAt(transaction.getCreatedAt());
        vo.setRemark(transaction.getRemark());
        
        // 查询商品信息
        Product product = productRepository.findById(transaction.getProductId()).orElse(null);
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductSpec(product.getSpecification());
            vo.setManufacturer(product.getManufacturer());
            vo.setMainUnit(product.getMainUnit());
            vo.setAuxUnit(product.getAuxUnit());
            vo.setMainToAuxRatio(product.getMainToAuxRatio());
        }
        
        // 查询门店信息
        Store store = storeRepository.findById(transaction.getStoreId()).orElse(null);
        if (store != null) {
            vo.setStoreName(store.getName());
        }
        
        // 查询操作人信息
        if (transaction.getCreatedBy() != null) {
            Staff staff = staffRepository.findById(transaction.getCreatedBy()).orElse(null);
            if (staff != null) {
                vo.setCreatedByName(staff.getName());
            }
        }
        
        // 设置类型名称
        InventoryTransactionType type = InventoryTransactionType.fromCode(transaction.getTransactionType());
        vo.setTypeName(type != null ? type.getDesc() : "未知");
        
        // 设置关联单号
        if (transaction.getReferenceType() != null && transaction.getReferenceId() != null) {
            String refType = transaction.getReferenceType();
            // 采购入库：关联采购单号
            if ("PURCHASE".equalsIgnoreCase(refType)) {
                try {
                    purchaseRepository.findById(transaction.getReferenceId()).ifPresent(purchase -> {
                        String ref = purchase.getPurchaseNo() != null
                                ? purchase.getPurchaseNo()
                                : String.valueOf(purchase.getId());
                        vo.setReferenceNo(ref);
                    });
                } catch (Exception e) {
                    // 查询失败时使用通用格式
                    vo.setReferenceNo(refType + "-" + transaction.getReferenceId());
                }
            }
            // 售后退回：关联售后单号
            else if ("SALES_RETURN".equalsIgnoreCase(refType)) {
                try {
                    afterSalesRepository.findById(transaction.getReferenceId()).ifPresent(afterSales -> {
                        String ref = (afterSales.getAfterSalesNo() != null && !afterSales.getAfterSalesNo().trim().isEmpty())
                                ? afterSales.getAfterSalesNo()
                                : ("SALES_RETURN-" + afterSales.getId());
                        vo.setReferenceNo(ref);
                    });
                    // 如果查询失败或不存在，使用通用格式
                    if (vo.getReferenceNo() == null) {
                        vo.setReferenceNo("SALES_RETURN-" + transaction.getReferenceId());
                    }
                } catch (Exception e) {
                    // 查询失败时使用通用格式，记录异常但不抛出
                    vo.setReferenceNo("SALES_RETURN-" + transaction.getReferenceId());
                }
            }
            // 销售相关出入库：关联订单号（线下 POS / 线上订单等）
            // 只要 referenceType 是 "ORDER"，就查询订单号，不限制 transactionType
            else if ("ORDER".equalsIgnoreCase(refType)) {
                try {
                    var orderOpt = orderRepository.findById(transaction.getReferenceId());
                    if (orderOpt.isPresent()) {
                        var order = orderOpt.get();
                        // 优先使用订单号，如果订单号为空则使用订单ID
                        String ref = (order.getOrderNo() != null && !order.getOrderNo().trim().isEmpty())
                                ? order.getOrderNo()
                                : ("ORDER-" + order.getId());
                        vo.setReferenceNo(ref);
                    } else {
                        // 订单不存在，使用订单ID作为关联单号
                        vo.setReferenceNo("ORDER-" + transaction.getReferenceId());
                    }
                } catch (Exception e) {
                    // 查询失败时使用通用格式，记录异常但不抛出
                    vo.setReferenceNo("ORDER-" + transaction.getReferenceId());
                }
            }
            // 其他类型：退回到通用格式
            else {
                vo.setReferenceNo(refType + "-" + transaction.getReferenceId());
            }
        }
        
        return vo;
    }
}

