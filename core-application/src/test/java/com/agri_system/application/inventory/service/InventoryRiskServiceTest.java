package com.agri_system.application.inventory.service;

import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.dto.command.InventoryDecisionCommand;
import com.agri_system.application.inventory.dto.vo.InventoryRiskVO;
import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.service.ManualPriceAuditService;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.application.order.service.OrderCostAllocationService;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.application.order.service.PickupFulfillmentService;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.inventory.model.InventoryDecisionLog;
import com.agri_system.domain.inventory.model.InventoryReceiptBatch;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.inventory.repository.InventoryDecisionLogRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository;
import com.agri_system.domain.inventory.service.BatchBalanceService;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderPickupRequestRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.inventory.query.SalesVelocity;
import com.agri_system.domain.inventory.query.PlanningStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task8 库存风险建议与人工决策留痕。
 *
 * <p>日期、库存与阈值只做成<b>可解释的提示</b>：未知日期必须显示为未知（既不安全也不阻断），
 * 临期/过期仅提示，绝不自动禁售/报损/调拨/采购；人工决定单独追加留痕。</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryRiskServiceTest {

    private static final Long STORE_ID = 1L;
    private static final Long STAFF_ID = 7L;
    private static final Long PRODUCT_ID = 99L;
    private static final Long ORDER_ID = 500L;
    private static final Long ITEM_ID = 900L;
    private static final Long BATCH_ID = 61L;
    private static final Long DECISION_ID = 501L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository itemRepository;
    @Mock
    private OrderPaymentRepository paymentRepository;
    @Mock
    private OrderPaymentIntentRepository paymentIntentRepository;
    @Mock
    private OrderDeliveryRepository deliveryRepository;
    @Mock
    private OrderDomainService orderDomainService;
    @Mock
    private OrderPaymentService paymentService;
    @Mock
    private InventoryApplicationService inventoryService;
    @Mock
    private WalletApplicationService walletService;
    @Mock
    private CreditApplicationService creditService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductPricingService productPricingService;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AfterSalesRepository afterSalesRepository;
    @Mock
    private AfterSalesItemRepository afterSalesItemRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private WalletAuthCodeRepository walletAuthCodeRepository;
    @Mock
    private BatchBalanceService batchBalanceService;
    @Mock
    private ProductUnitService productUnitService;
    @Mock
    private InventoryLocationRepository locationRepository;
    @Mock
    private InventoryBatchBalanceRepository batchBalanceRepository;
    @Mock
    private InventoryReceiptBatchRepository receiptBatchRepository;
    @Mock
    private OrderCostAllocationService orderCostAllocationService;
    @Mock
    private ManualPriceAuditService manualPriceAuditService;
    @Mock
    private OrderItemBatchAllocationRepository allocationRepository;
    @Mock
    private OrderPickupRequestRepository pickupRequestRepository;
    @Mock
    private InventoryDecisionLogRepository decisionLogRepository;

    @Mock private com.agri_system.domain.inventory.query.InventoryPlanningQuery planningQuery;
    private InventoryRiskService riskService;
    private OrderApplicationService orderService;

    @BeforeEach
    void setUp() {
        riskService = new InventoryRiskService(
                batchBalanceRepository, receiptBatchRepository, productRepository,
                allocationRepository, planningQuery, decisionLogRepository);
        orderService = new OrderApplicationService(
                orderRepository, itemRepository, paymentRepository, paymentIntentRepository, deliveryRepository,
                orderDomainService, paymentService, inventoryService, walletService, creditService,
                productRepository, productPricingService, org.mockito.Mockito.mock(com.agri_system.application.productpackage.service.PackagePricingService.class), staffRepository, storeRepository, customerRepository,
                afterSalesRepository, afterSalesItemRepository, eventPublisher, walletAuthCodeRepository,
                new PickupFulfillmentService(allocationRepository, pickupRequestRepository, locationRepository, receiptBatchRepository,
                        orderRepository, itemRepository, productRepository, afterSalesRepository,
                        org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class),staffRepository),
                manualPriceAuditService, org.mockito.Mockito.mock(com.agri_system.domain.system.log.repository.SysOperationLogRepository.class), null, null, null);
    }

    @Test
    void stockThresholdMustCompareMainUnitsInsteadOfBasePieces() {
        Product product=new Product();product.setId(PRODUCT_ID);product.setName("箱装商品");product.setMainUnit("箱");product.setMinStock(new BigDecimal("3"));product.setMaxStock(new BigDecimal("20"));
        when(productRepository.findAllWithMinStock()).thenReturn(List.of(product));
        org.mockito.Mockito.lenient().when(planningQuery.stocks(STORE_ID)).thenReturn(Map.of(PRODUCT_ID,new com.agri_system.domain.inventory.query.PlanningStock("箱",new BigDecimal("12"),new BigDecimal("2"))));
        var risks=riskService.list(STORE_ID);
        assertThat(risks).extracting(InventoryRiskVO::kind).containsExactly("LOW_STOCK");assertThat(risks.get(0).getCurrentStock()).isEqualByComparingTo("2");
    }

    @Test
    void knownExpiryWarningRemainsVisibleWhenMainUnitConversionIsUnknown() {
        when(receiptBatchRepository.findByStore(STORE_ID)).thenReturn(List.of(knownDateBatch(BATCH_ID,"EXPIRED-UNKNOWN-UNIT",LocalDate.now().minusDays(2))));
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByBatch(STORE_ID)).thenReturn(Map.of(BATCH_ID,new BigDecimal("5")));
        when(planningQuery.stocks(STORE_ID)).thenReturn(Map.of(PRODUCT_ID,new PlanningStock("箱",null,null)));
        var risks=riskService.list(STORE_ID);assertThat(risks).extracting(InventoryRiskVO::kind).containsExactly("EXPIRED");assertThat(risks.get(0).getCurrentStock()).isNull();assertThat(risks.get(0).getUnit()).isEqualTo("箱");
    }

    @Test
    void dateUnknownIsVisibleAsUnknownRatherThanSafeOrBlocked() {
        when(receiptBatchRepository.findByStore(STORE_ID))
                .thenReturn(List.of(unknownDateBatch(BATCH_ID, "B-UNKNOWN-61", new BigDecimal("10"))));
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByBatch(STORE_ID))
                .thenReturn(Map.of(BATCH_ID, new BigDecimal("10")));
        when(planningQuery.stocks(STORE_ID))
                .thenReturn(Map.of(PRODUCT_ID, new PlanningStock("袋",BigDecimal.ONE,new BigDecimal("10"))));
        when(productRepository.findAllWithMinStock()).thenReturn(Collections.emptyList());
        when(productRepository.findAllWithMaxStock()).thenReturn(Collections.emptyList());
        stubPosOrderThenSave();

        assertThat(riskService.list(1L)).extracting(InventoryRiskVO::kind).contains("DATE_UNKNOWN");
        assertThat(com.agri_system.application.order.service.PosOrderTestFixture.withPos(orderService).createPOSOrder(commandForUnknownDateSku(), 7L)).isNotNull();
    }

    @Test
    void expiredAndExpiringBatchesAreAdvisoryAndOnlyWithRemainingBalance() {
        when(receiptBatchRepository.findByStore(STORE_ID)).thenReturn(List.of(
                knownDateBatch(61L, "B-EXPIRED", LocalDate.now().minusDays(5)),
                knownDateBatch(62L, "B-EXPIRING", LocalDate.now().plusDays(10)),
                knownDateBatch(63L, "B-EMPTY", LocalDate.now().minusDays(5)),
                knownDateBatch(64L, "B-FAR", LocalDate.now().plusDays(200))));
        when(batchBalanceRepository.sumAvailableBaseQuantityGroupedByBatch(STORE_ID)).thenReturn(Map.of(
                61L, new BigDecimal("5"),
                62L, new BigDecimal("3"),
                63L, BigDecimal.ZERO,
                64L, new BigDecimal("7")));

        when(planningQuery.stocks(STORE_ID)).thenReturn(Map.of(PRODUCT_ID,new PlanningStock("袋",BigDecimal.ONE,new BigDecimal("15"))));
        List<InventoryRiskVO> risks = riskService.list(STORE_ID);

        assertThat(risks).extracting(InventoryRiskVO::kind).containsExactly("EXPIRED", "EXPIRING");
        assertThat(risks).extracting(InventoryRiskVO::batchId).containsExactly(61L, 62L);

        InventoryRiskVO expired = risks.get(0);
        assertThat(expired.getInternalReceiptBatchNo()).isEqualTo("B-EXPIRED");
        assertThat(expired.getCurrentStock()).isEqualByComparingTo("5");
        assertThat(expired.getDaysToExpiry()).isNegative();
        assertThat(expired.getConfidence()).isEqualTo("SUFFICIENT_DATA");
        assertThat(expired.getMessage()).contains("B-EXPIRED");

        InventoryRiskVO expiring = risks.get(1);
        assertThat(expiring.getDaysToExpiry()).isEqualTo(10);
        assertThat(expiring.getMessage()).contains("临期");
    }

    @Test
    void configuredThresholdWithoutBalancesIsDataInsufficientNotZeroStock() {
        Product configured = new Product();
        configured.setId(88L);
        configured.setName("尿素");
        configured.setCode("P088");
        configured.setMinStock(new BigDecimal("100"));
        when(productRepository.findAllWithMinStock()).thenReturn(List.of(configured));

        List<InventoryRiskVO> risks = riskService.list(STORE_ID);

        assertThat(risks).hasSize(1);
        InventoryRiskVO risk = risks.get(0);
        assertThat(risk.getKind()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(risk.getCurrentStock()).isNull();
        assertThat(risk.getMinStock()).isEqualByComparingTo("100");
        assertThat(risk.getConfidence()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(risk.getMessage()).contains("无法判断").doesNotContain("未启用");
    }

    @Test
    void lowStockAndOverstockAreAdvisoryWithWindowAndEvidence() {
        Product low = new Product();
        low.setId(1L);
        low.setName("复合肥");
        low.setCode("P001");
        low.setMinStock(new BigDecimal("100"));
        Product over = new Product();
        over.setId(2L);
        over.setName("地膜");
        over.setCode("P002");
        over.setMaxStock(new BigDecimal("50"));
        when(productRepository.findAllWithMinStock()).thenReturn(List.of(low));
        when(productRepository.findAllWithMaxStock()).thenReturn(List.of(over));
        when(planningQuery.stocks(STORE_ID))
                .thenReturn(Map.of(1L,new PlanningStock("袋",BigDecimal.ONE,new BigDecimal("30")),2L,new PlanningStock("袋",BigDecimal.ONE,new BigDecimal("80"))));
        when(planningQuery.salesVelocity(90, STORE_ID)).thenReturn(List.of(
                velocityRow(1L, "20", "2"), velocityRow(2L, "40", "8")));

        List<InventoryRiskVO> risks = riskService.list(STORE_ID);

        assertThat(risks).extracting(InventoryRiskVO::kind).containsExactly("LOW_STOCK", "OVERSTOCK");
        InventoryRiskVO lowRisk = risks.get(0);
        assertThat(lowRisk.getCurrentStock()).isEqualByComparingTo("30");
        assertThat(lowRisk.getMaxStock()).isNull();
        assertThat(lowRisk.getObservationWindowDays()).isEqualTo(90);
        assertThat(lowRisk.getEvidenceCount()).isEqualTo(2);
        assertThat(lowRisk.getConfidence()).isEqualTo("SUFFICIENT_DATA");
        InventoryRiskVO overRisk = risks.get(1);
        assertThat(overRisk.getMinStock()).isNull();
        assertThat(overRisk.getEvidenceCount()).isEqualTo(8);
    }

    @Test
    void lowMarginIsAdvisoryAndHealthyMarginProducesNoRow() {
        when(allocationRepository.sumWindowMarginByProduct(90, STORE_ID)).thenReturn(List.of(
                new OrderItemBatchAllocationRepository.WindowMargin(PRODUCT_ID, new BigDecimal("100.00"), new BigDecimal("97.00"), 3),
                new OrderItemBatchAllocationRepository.WindowMargin(2L, new BigDecimal("200.00"), new BigDecimal("100.00"), 5)));

        List<InventoryRiskVO> risks = riskService.list(STORE_ID);

        assertThat(risks).extracting(InventoryRiskVO::kind).containsExactly("LOW_MARGIN");
        InventoryRiskVO risk = risks.get(0);
        assertThat(risk.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(risk.getEvidenceCount()).isEqualTo(3);
        assertThat(risk.getMessage()).contains("90").contains("3.00%");
    }

    @Test
    void recordDecisionPersistsHumanChoiceAndRejectsBlankReasonOrUnknownAction() {
        when(decisionLogRepository.save(any(InventoryDecisionLog.class))).thenAnswer(invocation -> {
            InventoryDecisionLog saved = invocation.getArgument(0);
            saved.setId(DECISION_ID);
            return saved;
        });
        InventoryDecisionCommand command = decisionCommand("EXPIRING", "BATCH", BATCH_ID, "INSPECT", "TRANSFER", "临期批次转门店B促销");

        Long decisionId = riskService.recordDecision(command, STAFF_ID);

        assertThat(decisionId).isEqualTo(DECISION_ID);
        ArgumentCaptor<InventoryDecisionLog> captor = ArgumentCaptor.forClass(InventoryDecisionLog.class);
        verify(decisionLogRepository).save(captor.capture());
        InventoryDecisionLog saved = captor.getValue();
        assertThat(saved.getRiskKind()).isEqualTo("EXPIRING");
        assertThat(saved.getEntityType()).isEqualTo("BATCH");
        assertThat(saved.getEntityId()).isEqualTo(BATCH_ID);
        assertThat(saved.getSuggestedAction()).isEqualTo("INSPECT");
        assertThat(saved.getDecisionAction()).isEqualTo("TRANSFER");
        assertThat(saved.getDecisionReason()).isEqualTo("临期批次转门店B促销");
        assertThat(saved.getOperatorId()).isEqualTo(STAFF_ID);
        assertThat(saved.getConfirmerId()).isNull();
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThatThrownBy(() -> riskService.recordDecision(
                decisionCommand("EXPIRING", "BATCH", BATCH_ID, "INSPECT", "TRANSFER", "   "), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> riskService.recordDecision(
                decisionCommand("EXPIRING", "BATCH", BATCH_ID, "INSPECT", "DELETE_ALL", "非法动作"), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> riskService.recordDecision(
                decisionCommand("NOT_A_RISK", "BATCH", BATCH_ID, "INSPECT", "TRANSFER", "未知风险类型"), STAFF_ID))
                .isInstanceOf(IllegalArgumentException.class);

        // 校验失败绝不落库：追加写只有一次
        verify(decisionLogRepository, times(1)).save(any(InventoryDecisionLog.class));
    }

    private InventoryDecisionCommand decisionCommand(String riskKind, String entityType, Long entityId,
                                                    String suggestedAction, String decisionAction, String reason) {
        InventoryDecisionCommand command = new InventoryDecisionCommand();
        command.setRiskKind(riskKind);
        command.setEntityType(entityType);
        command.setEntityId(entityId);
        command.setSuggestedAction(suggestedAction);
        command.setDecisionAction(decisionAction);
        command.setDecisionReason(reason);
        return command;
    }

    private POSOrderCreateCommand commandForUnknownDateSku() {
        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStaffId(STAFF_ID);
        command.setPaymentType(1);
        OrderItemDTO item = new OrderItemDTO();
        item.setProductId(PRODUCT_ID);
        item.setQuantity(new BigDecimal("5"));
        item.setUnit("袋");
        item.setUnitPrice(new BigDecimal("100.00"));
        command.setItems(Collections.singletonList(item));
        return command;
    }

    private void stubPosOrderThenSave() {
        Staff staff = new Staff();
        staff.setId(STAFF_ID);
        staff.setStoreId(STORE_ID);
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("复合肥");
        product.setMainUnit("袋");
        product.setAvgCostPrice(new BigDecimal("40.00"));
        product.setAllowSpecialFund(0);

        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(orderDomainService.generateOrderNo()).thenReturn("SO202609120001");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productPricingService.quote(any(com.agri_system.application.product.dto.vo.PriceContext.class)))
                .thenReturn(new com.agri_system.application.product.dto.vo.PriceQuote("risk-price", null, PRODUCT_ID,
                        STORE_ID, "POS", 1, null, "袋", 1L, LocalDate.now(), new BigDecimal("100.00"),
                        "RETAIL", null, false, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ORDER_ID);
            }
            return saved;
        });
        when(itemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ITEM_ID);
            }
            return saved;
        });
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
    }

    private InventoryReceiptBatch unknownDateBatch(Long id, String internalBatchNo, BigDecimal baseQuantity) {
        InventoryReceiptBatch batch = new InventoryReceiptBatch();
        batch.setId(id);
        batch.setProductId(PRODUCT_ID);
        batch.setStoreId(STORE_ID);
        batch.setInternalReceiptBatchNo(internalBatchNo);
        batch.setDateStatus("UNKNOWN");
        batch.setBaseQuantity(baseQuantity);
        return batch;
    }

    private InventoryReceiptBatch knownDateBatch(Long id, String internalBatchNo, LocalDate expiryDate) {
        InventoryReceiptBatch batch = new InventoryReceiptBatch();
        batch.setId(id);
        batch.setProductId(PRODUCT_ID);
        batch.setStoreId(STORE_ID);
        batch.setInternalReceiptBatchNo(internalBatchNo);
        batch.setDateStatus("KNOWN");
        batch.setProductionDate(expiryDate.minusDays(30));
        batch.setExpiryDate(expiryDate);
        batch.setBaseQuantity(new BigDecimal("10"));
        return batch;
    }

    private SalesVelocity velocityRow(Long productId,String totalSold,String orderCount) {
        return new SalesVelocity(productId,"商品"+productId,"P"+productId,"袋",BigDecimal.ONE,new BigDecimal(totalSold),Integer.valueOf(orderCount),null,null,null,0);
    }
}
