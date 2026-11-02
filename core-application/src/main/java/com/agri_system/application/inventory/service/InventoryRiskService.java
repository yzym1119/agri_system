package com.agri_system.application.inventory.service;

import com.agri_system.application.inventory.dto.command.InventoryDecisionCommand;
import com.agri_system.application.inventory.dto.vo.InventoryRiskVO;
import com.agri_system.domain.inventory.model.InventoryDecisionLog;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryDecisionLogRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository.WindowMargin;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.inventory.query.InventoryPlanningQuery;
import com.agri_system.domain.inventory.query.PlanningStock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 库存风险建议与人工决策留痕服务（零售事实链 Task8）。
 *
 * <p><b>只提示、绝不自动执行</b>：日期未知显示为未知（既不安全也不阻断），临期/过期、
 * 低库存/积压、低毛利一律只产出可解释的提示；未知数据使用 DATA_INSUFFICIENT，
 * 绝不把“查不到”当作 0 或“安全”。人工决定通过 {@link #recordDecision} 单独追加留痕，
 * 不触发销售/调整/采购/调拨等任何动作。</p>
 *
 * <p>每次 {@link #list(Long)} 只做固定次数的批量读取：批次余额按商品/批次各一次分组查询、
 * 收货批次一次、销售速度一次、毛利窗口一次、已配置阈值商品两次，绝不按商品或批次逐条查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRiskService {

    /** 观察窗口天数（销售与毛利统一口径）。 */
    public static final int OBSERVATION_WINDOW_DAYS = 90;
    /** 临期预警天数（到期日距今不超过该值即提示）。 */
    public static final int EXPIRY_WARN_DAYS = 30;
    /** 最低毛利阈值：低于该值仅提示，绝不自动改价。 */
    public static final BigDecimal MIN_MARGIN_RATE = new BigDecimal("0.05");

    /** 日期未知（既不安全也不阻断）。 */
    public static final String KIND_DATE_UNKNOWN = "DATE_UNKNOWN";
    /** 临期。 */
    public static final String KIND_EXPIRING = "EXPIRING";
    /** 已过期。 */
    public static final String KIND_EXPIRED = "EXPIRED";
    /** 低于已配置最低库存。 */
    public static final String KIND_LOW_STOCK = "LOW_STOCK";
    /** 高于已配置最高库存。 */
    public static final String KIND_OVERSTOCK = "OVERSTOCK";
    /** 批次成本口径毛利过低。 */
    public static final String KIND_LOW_MARGIN = "LOW_MARGIN";
    /** 数据不足，不可判断（绝不能当作 0 或安全）。 */
    public static final String KIND_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";

    private static final String CONFIDENCE_SUFFICIENT = "SUFFICIENT_DATA";
    private static final String CONFIDENCE_INSUFFICIENT = "INSUFFICIENT_DATA";

    private static final String ENTITY_TYPE_PRODUCT = "PRODUCT";
    private static final String ENTITY_TYPE_BATCH = "BATCH";
    private static final String DATE_STATUS_KNOWN = "KNOWN";
    private static final int MAX_DECISION_REASON_LENGTH = 1000;

    private static final Set<String> RISK_KINDS = Set.of(
            KIND_DATE_UNKNOWN, KIND_EXPIRING, KIND_EXPIRED,
            KIND_LOW_STOCK, KIND_OVERSTOCK, KIND_LOW_MARGIN, KIND_DATA_INSUFFICIENT);

    private static final Set<String> ENTITY_TYPES = Set.of(ENTITY_TYPE_PRODUCT, ENTITY_TYPE_BATCH);

    /** 人工决策动作词汇表（只留痕，不触发任何自动动作）。 */
    private static final Set<String> DECISION_ACTIONS = Set.of(
            "CONTINUE_SALE", "INSPECT", "REPLENISH", "TRANSFER",
            "RETURN_TO_VENDOR", "WRITE_OFF", "PAUSE_REPLENISHMENT", "NO_ACTION");

    private final InventoryBatchBalanceRepository batchBalanceRepository;
    private final InventoryReceiptBatchRepository receiptBatchRepository;
    private final ProductRepository productRepository;
    private final OrderItemBatchAllocationRepository allocationRepository;
    private final InventoryPlanningQuery planningQuery;
    private final InventoryDecisionLogRepository decisionLogRepository;

    /**
     * 汇总某门店的库存风险提示（按严重程度降序，同级别按商品/批次稳定排序）。
     *
     * @param storeId 门店ID（风险提示必须按门店口径）
     * @return 可解释的提示列表；只包含“有事实依据”的提示
     */
    @Transactional(readOnly = true)
    public List<InventoryRiskVO> list(Long storeId) {
        // 1) 每类事实只做一次批量读取
        Map<Long,PlanningStock> stocks=planningQuery.stocks(storeId);
        Map<Long,BigDecimal> availableByProduct=new LinkedHashMap<>();
        stocks.forEach((id,stock)->availableByProduct.put(id,stock.quantity()));
        Map<Long, BigDecimal> availableByBatch = safeGrouped(
                batchBalanceRepository.sumAvailableBaseQuantityGroupedByBatch(storeId));
        List<InventoryReceiptBatch> receiptBatches = nullSafe(receiptBatchRepository.findByStore(storeId));
        Map<Long, Integer> evidenceByProduct = countOrdersByProduct(storeId);
        List<WindowMargin> margins = nullSafe(allocationRepository.sumWindowMarginByProduct(OBSERVATION_WINDOW_DAYS, storeId));
        Map<Long, Product> configuredProducts = loadConfiguredProducts();

        // 2) 组装提示（商品名称解析带缓存，避免同类商品重复查询）
        List<InventoryRiskVO> risks = new ArrayList<>();
        Map<Long, Product> productCache = new HashMap<>();
        collectDateRisks(risks, receiptBatches, availableByBatch, stocks, evidenceByProduct, productCache, storeId);
        collectStockRisks(risks, configuredProducts, availableByProduct, evidenceByProduct, storeId);
        collectMarginRisks(risks, margins, availableByProduct, evidenceByProduct, productCache, storeId);
        risks.sort(InventoryRiskService::compareBySeverity);
        return risks;
    }

    /**
     * 记录一次人工库存风险决策（仅追加留痕）。
     *
     * <p>只写入 {@code inventory_decision_log}，绝不调用销售/调整/采购/调拨等服务，
     * 也不改动任何库存、价格或商品状态。</p>
     *
     * @param command    人工决策命令
     * @param operatorId 操作人ID（来自登录会话）
     * @return 留痕记录ID
     */
    public Long recordDecision(InventoryDecisionCommand command, Long operatorId) {
        if (command == null) {
            throw new IllegalArgumentException("决策命令不能为空");
        }
        String riskKind = trimToNull(command.getRiskKind());
        String entityType = trimToNull(command.getEntityType());
        String decisionAction = trimToNull(command.getDecisionAction());
        String decisionReason = command.getDecisionReason() == null ? null : command.getDecisionReason().trim();

        if (operatorId == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        if (!RISK_KINDS.contains(riskKind)) {
            throw new IllegalArgumentException("未知风险类型: " + riskKind);
        }
        if (!ENTITY_TYPES.contains(entityType)) {
            throw new IllegalArgumentException("未知实体类型: " + entityType);
        }
        if (command.getEntityId() == null) {
            throw new IllegalArgumentException("实体ID不能为空");
        }
        if (!DECISION_ACTIONS.contains(decisionAction)) {
            throw new IllegalArgumentException("未知决策动作: " + decisionAction);
        }
        if (decisionReason == null || decisionReason.isEmpty()) {
            throw new IllegalArgumentException("决策原因不能为空");
        }
        if (decisionReason.length() > MAX_DECISION_REASON_LENGTH) {
            throw new IllegalArgumentException("决策原因不能超过" + MAX_DECISION_REASON_LENGTH + "字");
        }

        InventoryDecisionLog decisionLog = new InventoryDecisionLog();
        decisionLog.setRiskKind(riskKind);
        decisionLog.setEntityType(entityType);
        decisionLog.setEntityId(command.getEntityId());
        decisionLog.setSuggestedAction(trimToNull(command.getSuggestedAction()));
        decisionLog.setDecisionAction(decisionAction);
        decisionLog.setDecisionReason(decisionReason);
        decisionLog.setOperatorId(operatorId);
        decisionLog.setConfirmerId(command.getConfirmerId());
        LocalDateTime now = LocalDateTime.now();
        decisionLog.setDecidedAt(now);
        decisionLog.setCreatedAt(now);

        InventoryDecisionLog saved = decisionLogRepository.save(decisionLog);
        log.info("库存风险人工决策已留痕: riskKind={}, entityType={}, entityId={}, decisionAction={}, operatorId={}",
                riskKind, entityType, command.getEntityId(), decisionAction, operatorId);
        return saved == null ? decisionLog.getId() : saved.getId();
    }

    // ==================== 日期风险 ====================

    private void collectDateRisks(List<InventoryRiskVO> risks,
                                  List<InventoryReceiptBatch> receiptBatches,
                                  Map<Long, BigDecimal> availableByBatch,
                                  Map<Long, PlanningStock> stocks,
                                  Map<Long, Integer> evidenceByProduct,
                                  Map<Long, Product> productCache,
                                  Long storeId) {
        LocalDate today = LocalDate.now();
        for (InventoryReceiptBatch batch : receiptBatches) {
            if (batch == null || batch.getId() == null) {
                continue;
            }
            BigDecimal available = availableByBatch.get(batch.getId());
            // 无剩余可用余额的批次不产生日期提示（没有可继续销售/处理的对象）
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LocalDate expiryDate = batch.getExpiryDate();
            boolean dateKnown = expiryDate != null;
            String kind;
            Integer daysToExpiry = null;
            if (!dateKnown) {
                kind = KIND_DATE_UNKNOWN;
            } else {
                daysToExpiry = (int) ChronoUnit.DAYS.between(today, expiryDate);
                if (daysToExpiry < 0) {
                    kind = KIND_EXPIRED;
                } else if (daysToExpiry <= EXPIRY_WARN_DAYS) {
                    kind = KIND_EXPIRING;
                } else {
                    continue;
                }
            }

            InventoryRiskVO risk = new InventoryRiskVO();
            risk.setKind(kind);
            risk.setProductId(batch.getProductId());
            Product product = resolveProduct(batch.getProductId(), productCache);
            if (product != null) {
                risk.setProductName(product.getName());
                risk.setProductCode(product.getCode());
                risk.setUnit(product.getMainUnit());
            }
            risk.setStoreId(storeId != null ? storeId : batch.getStoreId());
            risk.setBatchId(batch.getId());
            risk.setInternalReceiptBatchNo(batch.getInternalReceiptBatchNo());
            PlanningStock stock=stocks.get(batch.getProductId());
            risk.setUnit(stock!=null?stock.mainUnit():product==null?null:product.getMainUnit());
            risk.setCurrentStock(stock!=null&&stock.quantity()!=null&&stock.basePerMainUnit()!=null&&stock.basePerMainUnit().signum()>0
                    ?available.divide(stock.basePerMainUnit(),8,RoundingMode.HALF_UP):null);
            risk.setDateStatus(batch.getDateStatus());
            risk.setProductionDate(batch.getProductionDate());
            risk.setExpiryDate(expiryDate);
            risk.setDaysToExpiry(daysToExpiry);
            risk.setObservationWindowDays(OBSERVATION_WINDOW_DAYS);
            Integer evidence = batch.getProductId() == null ? null : evidenceByProduct.get(batch.getProductId());
            risk.setEvidenceCount(evidence == null ? 0 : evidence);
            // 日期状态本身有明确事实（KNOWN/UNKNOWN），因此日期提示的置信度固定为充足
            risk.setConfidence(CONFIDENCE_SUFFICIENT);
            risk.setMessage(dateMessage(kind, batch.getInternalReceiptBatchNo(), expiryDate, daysToExpiry));
            risks.add(risk);
        }
    }

    private String dateMessage(String kind, String internalReceiptBatchNo, LocalDate expiryDate, Integer daysToExpiry) {
        String batchLabel = internalReceiptBatchNo == null ? "-" : internalReceiptBatchNo;
        return switch (kind) {
            case KIND_DATE_UNKNOWN -> "批次 " + batchLabel + " 未登记生产/到期日期（日期状态未知）："
                    + "只提示人工核对实物标签后再决定是否继续销售，系统不会自动禁售或处理。";
            case KIND_EXPIRED -> "批次 " + batchLabel + " 已过期 " + Math.abs(daysToExpiry) + " 天（到期日 "
                    + expiryDate + "）：仅作提示，请人工决定是否停售/退换/报损，系统不会自动执行。";
            default -> "批次 " + batchLabel + " 临期：到期日 " + expiryDate + "，剩余 " + daysToExpiry
                    + " 天，请人工决定是否促销/调拨/退换，系统不会自动执行。";
        };
    }

    // ==================== 库存阈值风险 ====================

    private void collectStockRisks(List<InventoryRiskVO> risks,
                                   Map<Long, Product> configuredProducts,
                                   Map<Long, BigDecimal> availableByProduct,
                                   Map<Long, Integer> evidenceByProduct,
                                   Long storeId) {
        for (Product product : configuredProducts.values()) {
            BigDecimal available = availableByProduct.get(product.getId());
            if (available == null) {
                // 查不到批次余额绝不当作 0：阈值不可评估，只能提示数据不足
                risks.add(dataInsufficientRisk(product, evidenceByProduct, storeId));
                continue;
            }
            boolean low = product.getMinStock() != null && available.compareTo(product.getMinStock()) < 0;
            boolean over = product.getMaxStock() != null && available.compareTo(product.getMaxStock()) > 0;
            if (low) {
                InventoryRiskVO risk = productRisk(KIND_LOW_STOCK, product, available, evidenceByProduct, storeId);
                risk.setMessage("近 " + OBSERVATION_WINDOW_DAYS + " 天该商品有 " + risk.getEvidenceCount()
                        + " 单销售记录：当前可用库存 " + plain(available) + " 低于已配置最低库存 "
                        + plain(product.getMinStock()) + "，建议人工评估补货；系统不会自动采购。");
                risks.add(risk);
            } else if (over) {
                InventoryRiskVO risk = productRisk(KIND_OVERSTOCK, product, available, evidenceByProduct, storeId);
                risk.setMessage("近 " + OBSERVATION_WINDOW_DAYS + " 天该商品有 " + risk.getEvidenceCount()
                        + " 单销售记录：当前可用库存 " + plain(available) + " 高于已配置最高库存 "
                        + plain(product.getMaxStock()) + "，建议人工评估促销/调拨；系统不会自动处理。");
                risks.add(risk);
            }
        }
    }

    private InventoryRiskVO dataInsufficientRisk(Product product, Map<Long, Integer> evidenceByProduct, Long storeId) {
        InventoryRiskVO risk = productRisk(KIND_DATA_INSUFFICIENT, product, null, evidenceByProduct, storeId);
        risk.setConfidence(CONFIDENCE_INSUFFICIENT);
        risk.setMessage("该商品已配置库存阈值（最低库存 " + plain(product.getMinStock()) + "，最高库存 "
                + plain(product.getMaxStock()) + "），但本门店缺少可核对的批次余额或主单位换算证据，"
                + "无法判断当前库存是否充足；这不是 0 库存结论，请人工盘点后再决定，系统不会自动处理。");
        return risk;
    }

    private InventoryRiskVO productRisk(String kind, Product product, BigDecimal currentStock,
                                        Map<Long, Integer> evidenceByProduct, Long storeId) {
        InventoryRiskVO risk = new InventoryRiskVO();
        risk.setKind(kind);
        risk.setProductId(product.getId());
        risk.setProductName(product.getName());
        risk.setProductCode(product.getCode());
                risk.setUnit(product.getMainUnit());
        risk.setStoreId(storeId);
        risk.setCurrentStock(currentStock);
        risk.setMinStock(product.getMinStock());
        risk.setMaxStock(product.getMaxStock());
        risk.setObservationWindowDays(OBSERVATION_WINDOW_DAYS);
        Integer evidence = product.getId() == null ? null : evidenceByProduct.get(product.getId());
        risk.setEvidenceCount(evidence == null ? 0 : evidence);
        risk.setConfidence(evidence == null ? CONFIDENCE_INSUFFICIENT : CONFIDENCE_SUFFICIENT);
        return risk;
    }

    // ==================== 毛利风险 ====================

    private void collectMarginRisks(List<InventoryRiskVO> risks,
                                    List<WindowMargin> margins,
                                    Map<Long, BigDecimal> availableByProduct,
                                    Map<Long, Integer> evidenceByProduct,
                                    Map<Long, Product> productCache,
                                    Long storeId) {
        for (WindowMargin margin : margins) {
            if (margin == null || margin.productId() == null
                    || margin.revenue() == null || margin.totalCost() == null) {
                continue;
            }
            // 无收入不构成毛利证据
            if (margin.revenue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal marginRate = margin.revenue().subtract(margin.totalCost())
                    .divide(margin.revenue(), 4, RoundingMode.HALF_UP);
            if (marginRate.compareTo(MIN_MARGIN_RATE) >= 0) {
                continue;
            }

            int orderCount = margin.orderCount() == null ? 0 : margin.orderCount();
            InventoryRiskVO risk = new InventoryRiskVO();
            risk.setKind(KIND_LOW_MARGIN);
            risk.setProductId(margin.productId());
            Product product = resolveProduct(margin.productId(), productCache);
            if (product != null) {
                risk.setProductName(product.getName());
                risk.setProductCode(product.getCode());
                risk.setUnit(product.getMainUnit());
            }
            risk.setStoreId(storeId);
            risk.setCurrentStock(availableByProduct.get(margin.productId()));
            risk.setObservationWindowDays(OBSERVATION_WINDOW_DAYS);
            risk.setEvidenceCount(orderCount);
            risk.setConfidence(CONFIDENCE_SUFFICIENT);
            risk.setMessage("近 " + OBSERVATION_WINDOW_DAYS + " 天批次成本口径毛利 "
                    + marginRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
                    + "（销售额 " + plain(margin.revenue()) + "，批次成本 " + plain(margin.totalCost())
                    + "，订单数 " + orderCount + "），低于 5% 阈值：建议人工核查定价或促销，系统不会自动改价。");
            risks.add(risk);
        }
    }

    // ==================== 读取与组装辅助 ====================

    private Map<Long, Integer> countOrdersByProduct(Long storeId) {
        var rows=planningQuery.salesVelocity(OBSERVATION_WINDOW_DAYS,storeId);
        Map<Long,Integer> evidence=new LinkedHashMap<>();
        for(var row:rows)if(row.productId()!=null)evidence.put(row.productId(),row.orderCount()==null?0:row.orderCount());
        return evidence;
    }

    /** 合并“配置了最低库存”和“配置了最高库存”的商品，同ID合并字段而不是互相覆盖。 */
    private Map<Long, Product> loadConfiguredProducts() {
        Map<Long, Product> configured = new LinkedHashMap<>();
        for (Product product : nullSafe(productRepository.findAllWithMinStock())) {
            if (product != null && product.getId() != null) {
                configured.put(product.getId(), product);
            }
        }
        for (Product product : nullSafe(productRepository.findAllWithMaxStock())) {
            if (product == null || product.getId() == null) {
                continue;
            }
            Product existing = configured.get(product.getId());
            if (existing == null) {
                configured.put(product.getId(), product);
                continue;
            }
            if (existing.getMinStock() == null) existing.setMinStock(product.getMinStock());
            if (existing.getMaxStock() == null) existing.setMaxStock(product.getMaxStock());
            if (existing.getReorderQuantity() == null) existing.setReorderQuantity(product.getReorderQuantity());
        }
        return configured;
    }

    private Product resolveProduct(Long productId, Map<Long, Product> productCache) {
        if (productId == null) {
            return null;
        }
        if (productCache.containsKey(productId)) {
            return productCache.get(productId);
        }
        Optional<Product> found = productRepository.findById(productId);
        Product product = found == null ? null : found.orElse(null);
        productCache.put(productId, product);
        return product;
    }

    private static int compareBySeverity(InventoryRiskVO a, InventoryRiskVO b) {
        int byKind = Integer.compare(kindRank(a.getKind()), kindRank(b.getKind()));
        if (byKind != 0) {
            return byKind;
        }
        int byProduct = Long.compare(nullSafeId(a.getProductId()), nullSafeId(b.getProductId()));
        if (byProduct != 0) {
            return byProduct;
        }
        return Long.compare(nullSafeId(a.getBatchId()), nullSafeId(b.getBatchId()));
    }

    private static int kindRank(String kind) {
        if (kind == null) {
            return 99;
        }
        return switch (kind) {
            case KIND_EXPIRED -> 0;
            case KIND_EXPIRING -> 1;
            case KIND_DATE_UNKNOWN -> 2;
            case KIND_LOW_STOCK -> 3;
            case KIND_OVERSTOCK -> 4;
            case KIND_LOW_MARGIN -> 5;
            case KIND_DATA_INSUFFICIENT -> 6;
            default -> 99;
        };
    }

    private static long nullSafeId(Long id) {
        return id == null ? Long.MAX_VALUE : id;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static Map<Long, BigDecimal> safeGrouped(Map<Long, BigDecimal> grouped) {
        return grouped == null ? Collections.emptyMap() : grouped;
    }

}
