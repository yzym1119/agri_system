package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.command.InventoryTransferCommand;
import com.agri_system.application.inventory.dto.command.InventoryLocationCommand;
import com.agri_system.application.inventory.dto.command.InventoryMoveCommand;
import com.agri_system.application.inventory.dto.query.InventoryQuery;
import com.agri_system.application.inventory.dto.query.InventoryTransferRecordQuery;
import com.agri_system.application.inventory.dto.query.InventoryTransactionQuery;
import com.agri_system.application.inventory.dto.vo.BatchDetailVO;
import com.agri_system.application.inventory.dto.vo.InventoryDetailVO;
import com.agri_system.application.inventory.dto.vo.InventoryLocationVO;
import com.agri_system.application.inventory.dto.vo.InventoryTransferRecordVO;
import com.agri_system.application.inventory.dto.vo.InventoryTransactionVO;
import com.agri_system.application.inventory.exception.InventoryErrorCode;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.inventory.enums.InventoryTransactionType;
import com.agri_system.domain.inventory.model.Inventory;
import com.agri_system.domain.inventory.model.InventoryBatch;
import com.agri_system.domain.inventory.model.InventoryTransferOrder;
import com.agri_system.domain.inventory.model.InventoryTransferOrderDetail;
import com.agri_system.domain.inventory.model.InventoryTransaction;
import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryTransferOrderDetailRepository;
import com.agri_system.domain.inventory.repository.InventoryTransferOrderRepository;
import com.agri_system.domain.inventory.repository.InventoryTransactionRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.product.service.UnitConversionSnapshot;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    
    private final InventoryLedgerService ledger;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryTransferOrderRepository transferOrderRepository;
    private final InventoryTransferOrderDetailRepository transferOrderDetailRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;
    private final PurchaseRepository purchaseRepository;
    private final JdbcTemplate jdbcTemplate;
    private final BatchBalanceService batchBalanceService;
    private final InventoryLocationRepository locationRepository;
    private final InventoryReceiptBatchRepository receiptBatchRepository;
    private final ProductUnitService productUnitService;

    @Transactional(rollbackFor = Exception.class)
    public Long createLocation(InventoryLocationCommand command) {
        InventoryLocation.LocationType type;
        try {
            type = InventoryLocation.LocationType.valueOf(command.getLocationType().trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("库存位置类型无效");
        }
        InventoryLocation location = new InventoryLocation();
        location.setStoreId(command.getStoreId());
        location.setLocationType(type.name());
        location.setCode(StringUtils.hasText(command.getCode()) ? command.getCode().trim() : null);
        location.setName(command.getName().trim());
        location.setStatus(1);
        location.setCreatedAt(LocalDateTime.now());
        location.setUpdatedAt(location.getCreatedAt());
        locationRepository.save(location);
        return location.getId();
    }

    /** 查询门店全部货位（管理端货位作业界面用）。 */
    public List<InventoryLocationVO> listLocations(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId 不能为空");
        }
        return locationRepository.findByStoreId(storeId).stream()
                .map(this::toLocationVO)
                .collect(Collectors.toList());
    }

    private InventoryLocationVO toLocationVO(InventoryLocation location) {
        InventoryLocationVO vo = new InventoryLocationVO();
        vo.setId(location.getId());
        vo.setStoreId(location.getStoreId());
        vo.setLocationType(location.getLocationType());
        vo.setCode(location.getCode());
        vo.setName(location.getName());
        vo.setStatus(location.getStatus());
        vo.setCreatedAt(location.getCreatedAt());
        return vo;
    }

    /** 新单位链路移动；调用者提供原始数量，基准数量和快照由服务统一生成。 */
    @Transactional(rollbackFor = Exception.class)
    public InventoryMovement move(InventoryMoveCommand command, Long createdBy) {
        InventoryReceiptBatch batch = receiptBatchRepository.findById(command.getBatchId())
                .orElseThrow(() -> new IllegalArgumentException("收货批次不存在"));
        BatchBalanceService.MovementPurpose purpose;
        try {
            purpose = BatchBalanceService.MovementPurpose.valueOf(command.getPurpose().trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("移动用途无效");
        }
        if (purpose == BatchBalanceService.MovementPurpose.SALE || purpose == BatchBalanceService.MovementPurpose.RECEIPT
                || purpose == BatchBalanceService.MovementPurpose.RETURN || purpose == BatchBalanceService.MovementPurpose.TRANSFER) {
            throw new IllegalArgumentException("销售、收货、退货和跨店调拨必须通过对应业务单据，不能手工创建移动");
        }
        ProductUnitService.UnitOperation operation = purpose == BatchBalanceService.MovementPurpose.TRANSFER
                ? ProductUnitService.UnitOperation.TRANSFER : ProductUnitService.UnitOperation.INVENTORY;
        var unit = productUnitService.requireEnabledUnit(batch.getProductId(), command.getOriginalUnit(), operation);
        UnitConversionSnapshot converted = productUnitService.toBaseQuantity(
                batch.getProductId(), command.getOriginalUnit(), command.getOriginalQuantity());
        return batchBalanceService.move(new BatchBalanceService.MoveRequest(
                batch.getId(), command.getFromLocationId(), command.getToLocationId(), purpose,
                command.getTransferId(), unit.getId(), converted.unitName(), converted.originalQuantity(),
                converted.baseQuantity(), batch.getBaseUnitCost(), command.getReferenceType(),
                command.getReferenceId(), createdBy, command.getRemark(), parseDirection(command.getDirection()),
                command.getIdempotencyKey()));
    }

    private BatchBalanceService.Direction parseDirection(String direction) {
        if (!StringUtils.hasText(direction)) return null;
        try {
            return BatchBalanceService.Direction.valueOf(direction.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("移动方向无效,仅支持IN/OUT");
        }
    }


    
    /**
     * 入库
     * 
     * @param command 入库命令
     * @param createdBy 操作人ID
     * @return 批次ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(InventoryInCommand command, Long createdBy) {
        return ledger.inbound(command, createdBy);
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
        return ledger.outbound(command, createdBy);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public void lockProducts(Long storeId, List<Long> productIds) {
        ledger.lockProducts(storeId, productIds);
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
        if (Objects.equals(command.getFromStoreId(), command.getToStoreId())) {
            throw new IllegalArgumentException(InventoryErrorCode.TARGET_STORE_UNAVAILABLE + ": 调出门店和调入门店不能相同");
        }

        storeRepository.findById(command.getFromStoreId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.TARGET_STORE_UNAVAILABLE + ": 调出门店不存在"));
        storeRepository.findById(command.getToStoreId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.TARGET_STORE_UNAVAILABLE + ": 调入门店不存在"));

        // 校验商品是否存在并获取单位
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                        InventoryErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        // 获取单位（优先使用主单位）
        String unit = StringUtils.hasText(command.getUnit()) ? command.getUnit() : product.getMainUnit();
        if (unit == null || unit.trim().isEmpty()) {
            throw new IllegalArgumentException("商品单位信息不完整");
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryTransferOrder order = new InventoryTransferOrder();
        if (!StringUtils.hasText(command.getRequestKey())) throw new IllegalArgumentException("调拨请求标识不能为空");
        order.setRequestKey(command.getRequestKey());
        order.setTransferNo(generateTransferNo(now.toLocalDate()));
        order.setFromStoreId(command.getFromStoreId());
        order.setToStoreId(command.getToStoreId());
        order.setProductId(command.getProductId());
        order.setQuantity(command.getQuantity());
        order.setUnit(unit);
        order.setStatus(3);
        order.setRemark(command.getRemark());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setCreatedBy(createdBy);
        order.setUpdatedBy(createdBy);
        transferOrderRepository.save(order);

        InventoryDomainService.TransferDetail transferDetail = ledger.transfer(
                command.getFromStoreId(),
                command.getToStoreId(),
                command.getProductId(),
                command.getQuantity(),
                unit,
                createdBy,
                order.getId()
        );

        List<InventoryTransferOrderDetail> detailRecords = new ArrayList<>();
        for (InventoryDomainService.TransferBatchDetail batchDetail : transferDetail.getTransferBatchDetails()) {
            InventoryTransferOrderDetail detailRecord = new InventoryTransferOrderDetail();
            detailRecord.setTransferId(order.getId());
            detailRecord.setFromReceiptBatchId(batchDetail.getFromBatchId());
            detailRecord.setToReceiptBatchId(batchDetail.getToBatchId());
            detailRecord.setQuantity(batchDetail.getQuantity());
            detailRecord.setCostPrice(batchDetail.getCostPrice());
            detailRecord.setCreatedAt(now);
            detailRecords.add(detailRecord);
        }
        transferOrderDetailRepository.saveBatch(detailRecords);

        transferDetail.setTransferId(order.getId());
        transferDetail.setTransferNo(order.getTransferNo());
        return transferDetail;
    }

    public Page<InventoryTransferRecordVO> queryTransferPage(InventoryTransferRecordQuery query) {
        StringBuilder fromSql = new StringBuilder("""
                FROM inventory_transfer o
                LEFT JOIN product p ON o.product_id = p.id
                LEFT JOIN store fs ON o.from_store_id = fs.id
                LEFT JOIN store ts ON o.to_store_id = ts.id
                LEFT JOIN staff st ON o.created_by = st.id
                LEFT JOIN (
                    SELECT transfer_id, COUNT(1) AS batch_count
                    FROM inventory_transfer_detail
                    GROUP BY transfer_id
                ) td ON td.transfer_id = o.id
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        if (query.getFromStoreId() != null) {
            fromSql.append(" AND o.from_store_id = ? ");
            params.add(query.getFromStoreId());
        }

        if (query.getToStoreId() != null) {
            fromSql.append(" AND o.to_store_id = ? ");
            params.add(query.getToStoreId());
        }

        if (StringUtils.hasText(query.getProductName())) {
            fromSql.append(" AND p.name LIKE ? ");
            params.add("%" + query.getProductName().trim() + "%");
        }

        if (StringUtils.hasText(query.getStartTime())) {
            fromSql.append(" AND o.created_at >= ? ");
            params.add(LocalDateTime.parse(query.getStartTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        if (StringUtils.hasText(query.getEndTime())) {
            fromSql.append(" AND o.created_at <= ? ");
            params.add(LocalDateTime.parse(query.getEndTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        String countSql = "SELECT COUNT(1) " + fromSql;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalRows = total == null ? 0L : total;

        String dataSql = """
                SELECT
                    o.id AS transfer_id,
                    o.transfer_no AS transfer_no,
                    o.from_store_id AS from_store_id,
                    fs.name AS from_store_name,
                    o.to_store_id AS to_store_id,
                    ts.name AS to_store_name,
                    o.product_id AS product_id,
                    p.name AS product_name,
                    p.specification AS product_spec,
                    o.quantity AS quantity,
                    COALESCE(td.batch_count, 0) AS batch_count,
                    o.created_by AS created_by,
                    st.name AS created_by_name,
                    o.created_at AS created_at,
                    o.remark AS remark
                """ + fromSql + """
                ORDER BY o.created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(query.getPageSize());
        dataParams.add((long) (query.getPageNum() - 1) * query.getPageSize());

        List<InventoryTransferRecordVO> records = jdbcTemplate.query(dataSql, dataParams.toArray(), (rs, rowNum) -> {
            InventoryTransferRecordVO vo = new InventoryTransferRecordVO();
            vo.setTransferId(rs.getLong("transfer_id"));
            vo.setTransferNo(rs.getString("transfer_no"));
            vo.setFromStoreId((Long) rs.getObject("from_store_id"));
            vo.setFromStoreName(rs.getString("from_store_name"));
            vo.setToStoreId((Long) rs.getObject("to_store_id"));
            vo.setToStoreName(rs.getString("to_store_name"));
            vo.setProductId(rs.getLong("product_id"));
            vo.setProductName(rs.getString("product_name"));
            vo.setProductSpec(rs.getString("product_spec"));
            vo.setQuantity(rs.getBigDecimal("quantity"));
            Number batchCount = (Number) rs.getObject("batch_count");
            vo.setBatchCount(batchCount == null ? 0 : batchCount.intValue());
            vo.setCreatedBy((Long) rs.getObject("created_by"));
            vo.setCreatedByName(rs.getString("created_by_name"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            vo.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
            vo.setRemark(rs.getString("remark"));
            return vo;
        });

        Page<InventoryTransferRecordVO> page = new Page<>();
        page.setRecords(records);
        page.setPageNumber(query.getPageNum());
        page.setPageSize(query.getPageSize());
        page.setTotalRow(totalRows);
        page.setTotalPage(query.getPageSize() == null || query.getPageSize() <= 0
                ? 0L
                : (long) Math.ceil((double) totalRows / query.getPageSize()));
        return page;
    }

    private String generateTransferNo(LocalDate date) {
        String prefix = "TRF" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String maxTransferNo = transferOrderRepository.findMaxTransferNoByPrefix(prefix);
        int sequence = 1;
        if (maxTransferNo != null && maxTransferNo.length() > prefix.length()) {
            try {
                sequence = Integer.parseInt(maxTransferNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                sequence = 1;
            }
        }
        return prefix + String.format("%06d", sequence);
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
                .orElseGet(() -> { Inventory empty = new Inventory(); empty.setQuantity(BigDecimal.ZERO); return empty; });
        List<BatchDetailVO> batches = ledger.batchViews(storeId, productId);

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
        vo.setBatches(batches);
        
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
                query.getProductName(),
                query.getOnlyHasStock());
    }
    
    /**
     * 查询批次详情
     * 
     * @param batchId 批次ID
     * @return 批次详情
     */
    public BatchDetailVO getBatchDetail(Long batchId) {
        return ledger.batchView(batchId);
    }
    
    /**
     * 库存调整（盘点）
     * 
     * @param command 调整命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(InventoryAdjustCommand command, Long createdBy) {
        ledger.adjust(command, createdBy);
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
        Store store = storeRepository.findByIdIncludingDeleted(transaction.getStoreId()).orElse(null);
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
            // 订单退款回库：关联订单号
            else if ("ORDER_REFUND".equalsIgnoreCase(refType)) {
                try {
                    var orderOpt = orderRepository.findById(transaction.getReferenceId());
                    if (orderOpt.isPresent()) {
                        var order = orderOpt.get();
                        String ref = (order.getOrderNo() != null && !order.getOrderNo().trim().isEmpty())
                                ? order.getOrderNo()
                                : ("ORDER_REFUND-" + order.getId());
                        vo.setReferenceNo(ref);
                    } else {
                        vo.setReferenceNo("ORDER_REFUND-" + transaction.getReferenceId());
                    }
                } catch (Exception e) {
                    vo.setReferenceNo("ORDER_REFUND-" + transaction.getReferenceId());
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
