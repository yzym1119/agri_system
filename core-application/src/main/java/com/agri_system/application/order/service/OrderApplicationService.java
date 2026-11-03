package com.agri_system.application.order.service;

import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.summarizePayment;
import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.resolveMainPaymentMethodName;
import static com.agri_system.application.order.service.OrderPaymentDisplayPolicy.resolvePaymentRecordDisplayName;
import com.agri_system.application.order.service.OrderPaymentDisplayPolicy.PaymentSummary;
import com.agri_system.application.order.service.OrderRefundHistory.RefundAggregate;
import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderPaymentIntent;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentIntentRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订单应用服务
 * 负责订单业务流程编排
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private static final Logger logger = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderPaymentRepository paymentRepository;
    private final OrderPaymentIntentRepository paymentIntentRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderDomainService orderDomainService;
    private final OrderPaymentService paymentService;
    private final InventoryApplicationService inventoryService;
    private final WalletApplicationService walletService;
    private final CreditApplicationService creditService;
    private final ProductRepository productRepository;
    private final ProductPricingService productPricingService;
    private final com.agri_system.application.productpackage.service.PackagePricingService packagePricingService;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final AfterSalesItemRepository afterSalesItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletAuthCodeRepository walletAuthCodeRepository;
    private final PickupFulfillmentService pickupFulfillmentService;
    private final ManualPriceAuditService manualPriceAuditService;
    private final com.agri_system.domain.system.log.repository.SysOperationLogRepository operationLogRepository;
    private final OrderQueryService queryService;
    private final OnlineOrderCreationService onlineOrderCreationService;
    private final PosOrderCreationService posOrderCreationService;

    @Value("${payment.client-confirm.enabled:false}")
    private boolean clientPaymentConfirmEnabled;
    
    /**
     * 创建小程序订单
     * 
     * @param command 订单创建命令
     * @param customerId 客户ID
     * @return 订单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateCommand command, Long customerId) {
        return onlineOrderCreationService.createOrder(command,customerId);
    }

    /**
     * 创建POS订单
     * 
     * @param command POS订单创建命令
     * @param createdBy 操作人ID
     * @return 订单ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long createPOSOrder(POSOrderCreateCommand command, Long createdBy) {
        return posOrderCreationService.createPOSOrder(command,createdBy);
    }
    
    /**
     * 订单支付（小程序兼容入口）
     *
     * 仅当订单无需外部支付时，直接完成内部支付并落单。
     * 若仍需微信/支付宝，则只返回预览，由前端继续走 payment-intent / payment-confirmation。
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OrderPayPreviewVO payOrder(OrderPayCommand command, Long customerId) {
        Order order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(()->new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND+": 订单不存在"));
        if(!java.util.Objects.equals(order.getCustomerId(),customerId) || customerId==null) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED+": 无权操作此订单");
        }
        if(!Integer.valueOf(1).equals(order.getOrderStatus())) {
            if(isCompletedClientPayment(order,command)) return null;
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID+": 订单状态不正确或原收款事实不一致");
        }
        customerRepository.findByIdWithLock(customerId).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        OrderPayPreviewVO preview = buildClientPaymentPreview(order, command);
        if (preview.getExternalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return preview;
        }

        paymentService.executePayment(
                order,
                command.getUseBalance(),
                command.getUseSpecialFund(),
                command.getCouponId(),
                customerId,
                false,
                null,
                null,
                null
        );

        finalizeClientOrderPayment(order, customerId);
        return null;
    }

    /**
     * 订单支付预览（小程序）
     */
    public OrderPayPreviewVO previewOrderPayment(OrderPayCommand command, Long customerId) {
        Order order = loadPendingCustomerOrder(command.getOrderId(), customerId);
        return buildClientPaymentPreview(order, command);
    }

    /**
     * 生成外部支付发起信息（小程序）
     */
    public OrderExternalPaymentIntentVO createExternalPaymentIntent(OrderPayCommand command, Long customerId) {
        Order order = loadPendingCustomerOrder(command.getOrderId(), customerId);
        OrderPayPreviewVO preview = buildClientPaymentPreview(order, command);
        if (preview.getExternalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 当前订单无需外部支付");
        }

        paymentIntentRepository.expirePendingByOrderId(order.getId());

        OrderPaymentIntent intentRecord = new OrderPaymentIntent();
        intentRecord.setIntentNo(generatePaymentIntentNo(order.getId()));
        intentRecord.setOrderId(order.getId());
        intentRecord.setCustomerId(customerId);
        intentRecord.setUseBalance(Boolean.TRUE.equals(command.getUseBalance()));
        intentRecord.setUseSpecialFund(Boolean.TRUE.equals(command.getUseSpecialFund()));
        intentRecord.setCouponId(command.getCouponId());
        intentRecord.setExternalPaymentType(resolveExternalPaymentType(command.getExternalPaymentType()));
        intentRecord.setExternalAmount(preview.getExternalAmount());
        intentRecord.setStatus(1);
        intentRecord.setExpireAt(LocalDateTime.now().plusMinutes(15));
        intentRecord.setCreatedAt(LocalDateTime.now());
        intentRecord.setUpdatedAt(LocalDateTime.now());
        paymentIntentRepository.save(intentRecord);

        OrderExternalPaymentIntentVO intent = new OrderExternalPaymentIntentVO();
        intent.setIntentNo(intentRecord.getIntentNo());
        intent.setOrderId(order.getId());
        intent.setOrderNo(order.getOrderNo());
        intent.setExternalPaymentType(intentRecord.getExternalPaymentType());
        intent.setExternalAmount(preview.getExternalAmount());
        intent.setExpireAt(intentRecord.getExpireAt());
        intent.setPreview(preview);
        return intent;
    }

    /**
     * 确认外部支付并完成订单（小程序）
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmExternalPayment(OrderExternalPaymentConfirmCommand command, Long customerId) {
        if (!clientPaymentConfirmEnabled) {
            throw new IllegalStateException("客户端确认支付已关闭，请等待支付平台回调确认");
        }
        OrderPaymentIntent intent = loadPaymentIntent(command.getIntentNo());
        if (intent.getCustomerId() == null || !intent.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权确认此支付");
        }
        if (command.getOrderId() == null || !command.getOrderId().equals(intent.getOrderId())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 支付意图与订单不一致");
        }
        confirmExternalPaymentIntent(intent, command.getExternalTransactionNo(), command.getExternalPaidAt(), customerId);
    }

    /**
     * 外部支付回调确认
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmExternalPaymentCallback(OrderExternalPaymentCallbackCommand command) {
        OrderPaymentIntent intent = loadPaymentIntent(command.getIntentNo());
        confirmExternalPaymentIntent(intent, command.getExternalTransactionNo(), command.getExternalPaidAt(), null);
    }
    
    /**
     * 分页查询订单列表
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    public com.mybatisflex.core.paginate.Page<OrderPageVO> queryOrderPage(OrderPageQuery query) {
        return queryService.queryOrderPage(query);
    }

    private Integer resolveDeliveryType(Order order) {
        if (order == null) return null;
        return deliveryRepository.findByOrderId(order.getId()).map(this::resolveDeliveryType)
                .orElse(java.util.Objects.equals(order.getOrderType(), 2) ? 1 : null);
    }

    private Integer resolveDeliveryType(OrderDelivery delivery) {
        return delivery == null ? null : delivery.getDeliveryType();
    }

    /**
     * 查询订单详情
     * 
     * @param orderId 订单ID
     * @return 订单详情
     */
    public OrderDetailVO getOrderDetail(Long orderId) {
        return queryService.getOrderDetail(orderId);
    }

    public OrderDetailVO getOrderDetailForCustomer(Long orderId, Long customerId) {
        return queryService.getOrderDetailForCustomer(orderId,customerId);
    }

    private java.util.Map<Long, RefundAggregate> loadOrderItemRefundAggregates(Long orderId) {
        return loadOrderItemRefundAggregates(orderId,null);
    }
    private java.util.Map<Long, RefundAggregate> loadOrderItemRefundAggregates(Long orderId,Long excludedAfterSalesId) {
        return OrderRefundHistory.load(orderId,excludedAfterSalesId,afterSalesRepository,afterSalesItemRepository);
    }

    private BigDecimal safeSubtract(BigDecimal total, BigDecimal used) {
        BigDecimal result = defaultAmount(total).subtract(defaultAmount(used));
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** 来源没有贡献金额时无法还原原选项；只核对本次请求仍允许原实际用到的来源。 */
    private boolean isCompletedClientPayment(Order order,OrderPayCommand command) {
        if(order.getOrderStatus()==null || !java.util.Set.of(2,3,4,5,6).contains(order.getOrderStatus())) return false;
        List<OrderPayment> sources=paymentRepository.findByOrderId(order.getId());
        if(sources==null || sources.isEmpty() || order.getPayAmount()==null || order.getCouponAmount()==null) return false;
        BigDecimal received=BigDecimal.ZERO,coupon=BigDecimal.ZERO;
        for(OrderPayment source:sources) {
            if(source.getAmount()==null || source.getAmount().signum()<0 || source.getPaymentType()==null) return false;
            if(source.getPaymentType()==PaymentMethod.COUPON.getCode()) {
                if(!java.util.Objects.equals(source.getCouponId(),command.getCouponId())) return false;
                coupon=coupon.add(source.getAmount());
            } else {
                if(source.getPaymentType()==PaymentMethod.BALANCE.getCode() && !Boolean.TRUE.equals(command.getUseBalance())) return false;
                if(source.getPaymentType()==PaymentMethod.SPECIAL_FUND.getCode() && !Boolean.TRUE.equals(command.getUseSpecialFund())) return false;
                if(source.getPaymentType()!=PaymentMethod.BALANCE.getCode() && source.getPaymentType()!=PaymentMethod.SPECIAL_FUND.getCode()
                        && source.getPaymentType()!=PaymentMethod.WECHAT.getCode() && source.getPaymentType()!=PaymentMethod.ALIPAY.getCode()) return false;
                if((source.getPaymentType()==PaymentMethod.WECHAT.getCode() || source.getPaymentType()==PaymentMethod.ALIPAY.getCode())
                        && !java.util.Objects.equals(source.getPaymentType(),resolveExternalPaymentType(command.getExternalPaymentType()))) return false;
                received=received.add(source.getAmount());
            }
        }
        if(command.getCouponId()!=null && coupon.signum()==0) return false;
        return received.compareTo(order.getPayAmount())==0 && coupon.compareTo(order.getCouponAmount())==0;
    }

    private Order loadPendingCustomerOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        if (order.getOrderStatus() == null || order.getOrderStatus() != 1) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
        }
        if (order.getCustomerId() == null || !order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权操作此订单");
        }
        return order;
    }

    private OrderPaymentIntent loadPaymentIntent(String intentNo) {
        return paymentIntentRepository.findByIntentNo(intentNo)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 支付意图不存在"));
    }

    private OrderPayPreviewVO buildClientPaymentPreview(Order order, OrderPayCommand command) {
        return paymentService.previewPayment(
                order,
                command.getUseBalance(),
                command.getUseSpecialFund(),
                command.getCouponId()
        );
    }

    private Integer resolveExternalPaymentType(Integer externalPaymentType) {
        if (externalPaymentType == null) {
            return OrderMainPaymentMethod.WECHAT.getCode();
        }
        if (!externalPaymentType.equals(OrderMainPaymentMethod.WECHAT.getCode())
                && !externalPaymentType.equals(OrderMainPaymentMethod.ALIPAY.getCode())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 外部支付方式不支持");
        }
        return externalPaymentType;
    }

    private void confirmExternalPaymentIntent(OrderPaymentIntent intent,
                                              String externalTransactionNo,
                                              LocalDateTime externalPaidAt,
                                              Long operatorId) {
        if (intent.getStatus() != null && intent.getStatus() == 2) {
            if (externalTransactionNo.equals(intent.getExternalTransactionNo())) {
                return;
            }
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 支付意图已被其他流水确认");
        }
        if (intent.getExpireAt() != null && intent.getExpireAt().isBefore(LocalDateTime.now())) {
            intent.setStatus(3);
            intent.setUpdatedAt(LocalDateTime.now());
            paymentIntentRepository.save(intent);
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 支付意图已过期，请重新发起支付");
        }

        Order order = orderRepository.findByIdForUpdate(intent.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        if (order.getOrderStatus() != null && order.getOrderStatus() != 1) {
            if (paymentRepository.findByOrderIdAndOuterTransNo(order.getId(), externalTransactionNo).isPresent()) {
                intent.setStatus(2);
                intent.setExternalTransactionNo(externalTransactionNo);
                if (intent.getConfirmedAt() == null) {
                    intent.setConfirmedAt(externalPaidAt != null ? externalPaidAt : LocalDateTime.now());
                }
                intent.setUpdatedAt(LocalDateTime.now());
                paymentIntentRepository.save(intent);
                return;
            }
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
        }

        customerRepository.findByIdWithLock(order.getCustomerId()).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        OrderPayPreviewVO preview = paymentService.previewPayment(
                order,
                intent.getUseBalance(),
                intent.getUseSpecialFund(),
                intent.getCouponId()
        );
        if (preview.getExternalAmount().compareTo(intent.getExternalAmount()) != 0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 支付意图金额已变化，请重新发起支付");
        }

        Long finalOperatorId = operatorId != null ? operatorId : intent.getCustomerId();
        List<OrderPayment> executed=paymentService.executePayment(
                order,
                intent.getUseBalance(),
                intent.getUseSpecialFund(),
                intent.getCouponId(),
                finalOperatorId,
                true,
                intent.getExternalPaymentType(),
                externalTransactionNo,
                externalPaidAt
        );

        BigDecimal actualExternal=executed.stream().filter(p->Integer.valueOf(PaymentMethod.WECHAT.getCode()).equals(p.getPaymentType())
                        || Integer.valueOf(PaymentMethod.ALIPAY.getCode()).equals(p.getPaymentType()))
                .map(OrderPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(actualExternal.compareTo(intent.getExternalAmount())!=0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH+": 实际外部收款与支付意图不一致");
        }
        finalizeClientOrderPayment(order, finalOperatorId);

        intent.setStatus(2);
        intent.setExternalTransactionNo(externalTransactionNo);
        intent.setConfirmedAt(externalPaidAt != null ? externalPaidAt : LocalDateTime.now());
        intent.setUpdatedAt(LocalDateTime.now());
        paymentIntentRepository.save(intent);
    }

    private String generatePaymentIntentNo(Long orderId) {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int randomPart = ThreadLocalRandom.current().nextInt(100, 1000);
        return "PI" + timePart + orderId + randomPart;
    }







    private void finalizeClientOrderPayment(Order order, Long customerId) {
        OrderDelivery delivery = null;
        if (Integer.valueOf(3).equals(order.getOrderType())) {
            delivery = deliveryRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> new IllegalArgumentException("配送订单缺少配送记录，请先核对订单"));
            if ((!Integer.valueOf(2).equals(delivery.getDeliveryType()) && !Integer.valueOf(3).equals(delivery.getDeliveryType()))
                    || !Integer.valueOf(1).equals(delivery.getDeliveryStatus())) {
                throw new IllegalArgumentException("配送方式或状态与待付款订单不一致，请先核对订单");
            }
        }
        order.markAsPaid();
        if (order.getOrderType() == 2) {
            order.markAsWaitPickup();
        } else if (order.getOrderType() == 3) {
            order.markAsWaitShip();
        }
        orderRepository.save(order);
        if (delivery != null) {
            delivery.setDeliveryStatus(2);
            deliveryRepository.save(delivery);
        }

        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        inventoryService.lockProducts(order.getStoreId(), items.stream().map(OrderItem::getProductId).toList());
        for (OrderItem item : items.stream().sorted(java.util.Comparator.comparing(OrderItem::getProductId)).toList()) {
            InventoryOutCommand outCommand = new InventoryOutCommand();
            outCommand.setStoreId(order.getStoreId());
            outCommand.setProductId(item.getProductId());
            outCommand.setQuantity(item.getQuantity());
            outCommand.setUnit(item.getUnit());
            outCommand.setReferenceType("ORDER");
            outCommand.setReferenceId(order.getId());
            outCommand.setReferenceLineId(item.getId());
            inventoryService.outbound(outCommand, null); // 客户不是员工，自动出库不冒用员工ID
        }
    }

    private void restockOrderItemsForRefund(Order order, List<RefundOrderItem> refundItems, Long createdBy) {
        if (refundItems == null || refundItems.isEmpty()) {
            return;
        }
        for (RefundOrderItem refundItem : refundItems.stream().sorted(java.util.Comparator.comparing(i -> i.orderItem().getProductId())).toList()) {
            OrderItem item = refundItem.orderItem();
            if (item.getProductId() == null
                    || refundItem.quantity() == null
                    || refundItem.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));

            InventoryInCommand inCommand = new InventoryInCommand();
            inCommand.setStoreId(order.getStoreId());
            inCommand.setProductId(item.getProductId());
            inCommand.setBatchNo(buildRefundBatchNo(order, item));
            inCommand.setQuantity(refundItem.quantity());
            inCommand.setUnit(item.getUnit());
            inCommand.setCostPrice(resolveRefundCostPrice(item));
            inCommand.setSourceType("ORDER_REFUND");
            inCommand.setSourceId(order.getId());
            inCommand.setSourceLineNo(Math.toIntExact(item.getId()));
            inCommand.setRemark("订单部分退款回库");
            inventoryService.inbound(inCommand, createdBy);
        }
    }

    private BigDecimal resolveRefundCostPrice(OrderItem item) {
        BigDecimal costPrice = item.getCostPriceSnapshot();
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            costPrice = item.getUnitPrice();
        }
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            costPrice = BigDecimal.ONE;
        }
        return costPrice;
    }

    private String buildRefundBatchNo(Order order, OrderItem item) {
        String orderNo = order.getOrderNo() != null && !order.getOrderNo().trim().isEmpty()
                ? order.getOrderNo()
                : "ORDER-" + order.getId();
        return "REFUND-" + orderNo + "-" + item.getId();
    }

    private RefundPlan buildRefundPlan(Order order, List<OrderItem> orderItems, OrderRefundCommand command) {
        return buildRefundPlan(order,orderItems,command,null);
    }
    private RefundPlan buildRefundPlan(Order order, List<OrderItem> orderItems, OrderRefundCommand command,Long excludedAfterSalesId) {
        BigDecimal alreadyRefunded = completedRefundAmount(order.getId());
        BigDecimal remainingPaid = safeSubtract(order.getPayAmount(), alreadyRefunded);
        Map<Long, RefundAggregate> existingRefunds = loadOrderItemRefundAggregates(order.getId(),excludedAfterSalesId);
        Map<Long, BigDecimal> refundBases = RefundAmountPolicy.refundableBaseAmounts(order, orderItems);
        BigDecimal refundAmount = command.getRefundAmount();
        List<OrderRefundCommand.RefundItem> commandItems = command.getItems();
        boolean hasItemDetails = commandItems != null && !commandItems.isEmpty();

        if (!hasItemDetails) {
            if (refundAmount == null) {
                refundAmount = remainingPaid;
            }
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额必须大于0");
            }
            if (refundAmount.compareTo(remainingPaid) > 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额不能超过订单实付金额");
            }
            if (refundAmount.compareTo(remainingPaid) < 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 部分退款必须指定退款商品明细");
            }
            List<RefundOrderItem> refundItems = new ArrayList<>();
            for (OrderItem item : orderItems) {
                RefundAggregate history = existingRefunds.get(item.getId());
                BigDecimal alreadyQty = history == null ? BigDecimal.ZERO : history.quantity;
                BigDecimal alreadyAmount = history == null ? BigDecimal.ZERO : history.amount;
                BigDecimal remainingQty = safeSubtract(item.getQuantity(), alreadyQty);
                if (remainingQty.signum() <= 0) continue;
                inventoryService.validateReturnQuantity(item, remainingQty);
                BigDecimal amount = RefundAmountPolicy.maximumForReturn(item, alreadyQty, alreadyAmount, remainingQty,
                        refundBases.getOrDefault(item.getId(), item.getTotalPrice()));
                refundItems.add(new RefundOrderItem(item, remainingQty, amount));
            }
            if (refundItems.isEmpty()) throw new IllegalArgumentException("没有剩余可退物料");
            if (refundItems.stream().map(RefundOrderItem::refundAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .compareTo(refundAmount) != 0) {
                throw new IllegalArgumentException("剩余退款金额与可退套餐物料不一致，请指定退货明细核对");
            }
            return new RefundPlan(refundAmount, refundItems, true);
        }

        Map<Long, OrderItem> orderItemMap = orderItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(OrderItem::getId, item -> item));

        Map<Long, RefundAggregate> requestedRefunds = new HashMap<>();
        java.util.Set<Long> requestedIds = new java.util.HashSet<>();
        List<RefundOrderItem> refundItems = new ArrayList<>();
        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        boolean zeroReturnAllowed = true;

        for (OrderRefundCommand.RefundItem commandItem : commandItems) {
            if (!requestedIds.add(commandItem.getOrderItemId())) throw new IllegalArgumentException("同一次退款不能重复提交订单行");
            OrderItem orderItem = Optional.ofNullable(orderItemMap.get(commandItem.getOrderItemId()))
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单明细不存在"));

            RefundAggregate aggregate = existingRefunds.get(orderItem.getId());
            RefundAggregate requestedAggregate = requestedRefunds.computeIfAbsent(orderItem.getId(), key -> new RefundAggregate());
            BigDecimal refundedQuantity = aggregate != null ? aggregate.quantity : BigDecimal.ZERO;
            BigDecimal refundedAmount = aggregate != null ? aggregate.amount : BigDecimal.ZERO;
            BigDecimal refundableQuantity = safeSubtract(orderItem.getQuantity(), refundedQuantity);
            BigDecimal refundableAmount = RefundAmountPolicy.maximumForReturn(orderItem, refundedQuantity, refundedAmount, commandItem.getQuantity(),
                    refundBases.getOrDefault(orderItem.getId(), orderItem.getTotalPrice()));
            inventoryService.validateReturnQuantity(orderItem, commandItem.getQuantity());

            BigDecimal requestedAmount=commandItem.getRefundAmount()==null?refundableAmount:commandItem.getRefundAmount();
            if(requestedAmount.signum()<0 || requestedAmount.stripTrailingZeros().scale()>2) throw new IllegalArgumentException("明细成交冲销金额必须为非负分金额");
            zeroReturnAllowed &= refundableAmount.signum() == 0;
            BigDecimal cumulativeRequestedQuantity = requestedAggregate.quantity.add(commandItem.getQuantity());
            BigDecimal cumulativeRequestedAmount = requestedAggregate.amount.add(requestedAmount);
            if (cumulativeRequestedQuantity.compareTo(refundableQuantity) > 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款数量不能超过当前可退数量");
            }
            if (cumulativeRequestedAmount.compareTo(refundableAmount) > 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 明细退款金额不能超过当前可退金额");
            }

            requestedAggregate.quantity = cumulativeRequestedQuantity;
            requestedAggregate.amount = cumulativeRequestedAmount;
            refundItems.add(new RefundOrderItem(orderItem, commandItem.getQuantity(), requestedAmount));
            totalRefundAmount = totalRefundAmount.add(requestedAmount);
        }

        if (refundAmount == null) {
            refundAmount = totalRefundAmount;
        } else if (refundAmount.compareTo(totalRefundAmount) != 0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 退款明细金额之和与退款总金额不一致");
        }

        if (refundAmount.signum() == 0 && !zeroReturnAllowed) {
            throw new IllegalArgumentException("0元退货仅用于本次无净成交可冲销的物料");
        }
        RefundAmountPolicy.validateOrderRefund(order.getPayAmount(), alreadyRefunded, refundAmount);
        if (refundAmount.compareTo(remainingPaid) > 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额不能超过订单实付金额");
        }

        boolean fullRefund = refundAmount.compareTo(remainingPaid) >= 0;
        return new RefundPlan(refundAmount, refundItems, fullRefund);
    }

    private BigDecimal completedRefundAmount(Long orderId) {
        List<com.agri_system.domain.after_sales.model.AfterSales> facts = afterSalesRepository.findByOrderId(orderId);
        if (facts == null) return BigDecimal.ZERO;
        return facts.stream().filter(f -> f != null && Integer.valueOf(com.agri_system.domain.after_sales.model.AfterSales.STATUS_FINISHED).equals(f.getStatus()))
                .map(f -> defaultAmount(f.getRefundAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private java.util.Map<Long, BigDecimal> extractRefundItemQuantities(List<RefundOrderItem> refundItems) {
        java.util.Map<Long, BigDecimal> quantities = new java.util.LinkedHashMap<>();
        if (refundItems == null || refundItems.isEmpty()) {
            return quantities;
        }
        for (RefundOrderItem refundItem : refundItems) {
            if (refundItem.orderItem().getId() == null || refundItem.quantity() == null) {
                continue;
            }
            quantities.merge(refundItem.orderItem().getId(), refundItem.quantity(), BigDecimal::add);
        }
        return quantities;
    }

    private record RefundOrderItem(OrderItem orderItem, BigDecimal quantity, BigDecimal refundAmount) {
    }

    private record RefundPlan(BigDecimal refundAmount, List<RefundOrderItem> refundItems, boolean fullRefund) {
    }
    
    /**
     * 确认收货（管理员强制确认）
     * 
     * @param orderId 订单ID
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmReceipt(Long orderId, Long createdBy) {
        confirmReceipt(orderId, createdBy, null);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmReceipt(Long orderId, Long createdBy, String confirmationKey) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        OrderDelivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单没有配送记录"));
        completeReceiptLocked(order, delivery, confirmationKey);
    }

    /** 手机本人签收也进入原订单锁；不能复用店长的整店发货权限或锁前授权。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Result confirmAssignedReceipt(Long orderId,String confirmationKey,Long staffId) {
        if(confirmationKey==null||!confirmationKey.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("请先核对本次配送商品与收货信息");
        Order order=orderRepository.findByIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("配送任务不存在或不属于本人"));
        OrderDelivery delivery=deliveryRepository.findByOrderId(orderId).orElseThrow(()->new IllegalArgumentException("配送任务不存在或不属于本人"));
        requireAssignedDelivery(order,delivery,staffId);
        if(!java.util.Objects.equals(delivery.getDeliveryStatus(),4)) {
            var preview=assignedPreviewLocked(order,delivery);
            if(!preview.canConfirm()||!confirmationKey.equals(preview.confirmationKey())) throw new IllegalArgumentException("配送任务或商品已变化，请重新核对");
            completeReceiptLocked(order,delivery,confirmationKey);
        }
        return new com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Result(order.getStoreId().toString(),order.getId().toString(),"COMPLETED",
                delivery.getReceivedAt()==null?null:delivery.getReceivedAt().toString());
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Preview previewAssignedReceipt(Long orderId,Long staffId) {
        Order order=orderRepository.findByIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("配送任务不存在或不属于本人"));
        OrderDelivery delivery=deliveryRepository.findByOrderId(orderId).orElseThrow(()->new IllegalArgumentException("配送任务不存在或不属于本人"));
        requireAssignedDelivery(order,delivery,staffId);
        return assignedPreviewLocked(order,delivery);
    }

    private void requireAssignedDelivery(Order order,OrderDelivery delivery,Long staffId) {
        Staff actor=staffId==null?null:staffRepository.findIdentityById(staffId).orElse(null);
        if(actor==null||!actor.isActive()||actor.hasBeenDeleted()||!java.util.Objects.equals(actor.getStoreId(),order.getStoreId())
                ||!java.util.Objects.equals(staffId,delivery.getDeliveryStaffId())||!java.util.Objects.equals(order.getId(),delivery.getOrderId())
                ||!java.util.Objects.equals(delivery.getDeliveryType(),2)) throw new IllegalArgumentException("配送任务不存在或不属于本人");
    }

    private com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Preview assignedPreviewLocked(Order order,OrderDelivery delivery) {
        boolean done=java.util.Objects.equals(delivery.getDeliveryStatus(),4);
        String blocked=null,key=null;
        List<com.agri_system.domain.order.model.DeliveryItemQuantity> items=List.of();
        if(!done) {
            if(!canDeliver(order,delivery,false)) blocked="当前任务暂停交付，请联系店长核对售后、订单状态及剩余物料";
            else if(!StringUtils.hasText(order.getReceiverName())||!StringUtils.hasText(order.getReceiverPhone())||!StringUtils.hasText(order.getReceiverAddress()))
                blocked="收货信息不完整，请联系店长核对";
            else {
                items=orderRepository.findRemainingDeliveryItems(order.getId());key=deliveryConfirmationKey(order,delivery,items);
                if(key==null) {blocked="缺少有效剩余物料，请联系店长核对";items=List.of();}
            }
        }
        boolean canConfirm=!done&&blocked==null;
        return new com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Preview(order.getStoreId().toString(),order.getId().toString(),order.getOrderNo(),done?"COMPLETED":"PENDING",
                canConfirm,blocked,canConfirm?order.getReceiverName():null,canConfirm?order.getReceiverPhone():null,canConfirm?order.getReceiverAddress():null,key,
                items.stream().map(item->new com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Item(item.getOrderItemId().toString(),item.getProductName(),item.getQuantity().stripTrailingZeros().toPlainString(),item.getUnit())).toList(),
                done&&delivery.getReceivedAt()!=null?delivery.getReceivedAt().toString():null);
    }

    private void completeReceiptLocked(Order order,OrderDelivery delivery,String confirmationKey) {
        Long orderId=order.getId();
        if (java.util.Objects.equals(delivery.getDeliveryStatus(), 4)) return;
        validateDeliveryAction(order, delivery, false);
        if (confirmationKey != null && !confirmationKey.equals(deliveryConfirmationKey(order, delivery,
                orderRepository.findRemainingDeliveryItems(orderId)))) {
            throw new IllegalArgumentException("配送商品或收货信息已变化，请重新核对后签收");
        }
        if (!java.util.Objects.equals(order.getOrderStatus(), 9)) {
            order.markAsFinished();
            orderRepository.save(order);
        }
        delivery.setDeliveryStatus(4);
        // received_at 为 DATETIME(0)，首次响应与数据库重读须使用同一秒精度。
        delivery.setReceivedAt(LocalDateTime.now().withNano(0));
        deliveryRepository.save(delivery);
        // POS 在开单时已经结算。线上订单仅在首次签收时结算。
        if (!java.util.Objects.equals(order.getOrderType(), 1)) {
            eventPublisher.publishEvent(new OrderFinishedEvent(order.getId()));
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmReceiptForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        if (order.getCustomerId() == null || !order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权操作此订单");
        }
        confirmReceipt(orderId, customerId);
    }
    
    /**
     * 自提核销
     * 
     * @param command 核销命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void verifyPickup(OrderPickupVerifyCommand command, Long createdBy) {
        // 自提码首读只定位；核销与原单退款争用同一父单，不能保存锁前对象覆盖退款。
        Order locator = orderRepository.findByPickupCode(command.getPickupCode())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.PICKUP_CODE_NOT_FOUND + ": 自提码不存在"));
        Order order = orderRepository.findByIdForUpdate(locator.getId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        if (!java.util.Objects.equals(order.getOrderType(), 2)
                || !java.util.Objects.equals(order.getPickupCode(), command.getPickupCode())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单不支持自提核销");
        }
        if (java.util.Objects.equals(order.getOrderStatus(), 6)) return;
        if (!java.util.Objects.equals(order.getOrderStatus(), 3)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
        }
        if (hasProcessingDeliveryAfterSales(order.getId()) || !orderRepository.hasRemainingDeliveryQuantity(order.getId())) {
            throw new IllegalArgumentException("订单当前不能自提：请核对处理中售后及剩余实出库物料");
        }
        
        order.markAsFinished();
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderFinishedEvent(order.getId()));
    }
    
    /**
     * 发货
     * 
     * @param command 发货命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void shipOrder(OrderShipCommand command, Long createdBy) {
        if (command == null || command.getOrderId() == null) throw new IllegalArgumentException("订单ID不能为空");
        Order order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        OrderDelivery delivery = deliveryRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("订单没有配送记录"));
        if (java.util.Objects.equals(delivery.getDeliveryStatus(), 3)
                || java.util.Objects.equals(delivery.getDeliveryStatus(), 4)) return;
        validateDeliveryAction(order, delivery, true);
        if (resolveDeliveryType(delivery) == 2) {
            if (command.getDeliveryStaffId() == null) throw new IllegalArgumentException("门店配送必须选择配送员");
            Staff driver = staffRepository.findById(command.getDeliveryStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("配送员不存在"));
            if (!driver.isActive() || driver.hasBeenDeleted() || !java.util.Objects.equals(driver.getStoreId(), order.getStoreId())) {
                throw new IllegalArgumentException("配送员必须是本店在职员工");
            }
            delivery.setDeliveryStaffId(driver.getId());
            delivery.setLogisticsCompany(null);
            delivery.setTrackingNo(null);
        } else {
            if (!StringUtils.hasText(command.getLogisticsCompany()) || !StringUtils.hasText(command.getTrackingNo())) {
                throw new IllegalArgumentException("快递发货必须填写物流公司和物流单号");
            }
            delivery.setDeliveryStaffId(null);
            delivery.setLogisticsCompany(command.getLogisticsCompany().trim());
            delivery.setTrackingNo(command.getTrackingNo().trim());
        }
        if (!java.util.Objects.equals(order.getOrderStatus(), 9)) {
            order.markAsShipped();
            orderRepository.save(order);
        }
        delivery.setDeliveryStatus(3);
        delivery.setDeliveryAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
    }

    public record ReassignmentPreview(String storeId,String orderId,String currentStaffId,String version,boolean canReassign,String blockReason) {}

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ReassignmentPreview previewDeliveryReassignment(Long orderId,Long operatorId) {
        Order order=orderRepository.findByIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("配送订单不存在"));
        OrderDelivery delivery=deliveryRepository.findByOrderId(orderId).orElseThrow(()->new IllegalArgumentException("订单没有配送记录"));
        requireDeliveryManager(order,operatorId);
        boolean available=resolveDeliveryType(delivery)==2 && canDeliver(order,delivery,false)
                && delivery.getAssignmentVersion()!=null && delivery.getAssignmentVersion()>=0 && delivery.getAssignmentVersion()<Long.MAX_VALUE;
        return new ReassignmentPreview(order.getStoreId().toString(),order.getId().toString(),delivery.getDeliveryStaffId()==null?null:delivery.getDeliveryStaffId().toString(),
                delivery.getAssignmentVersion()==null?null:delivery.getAssignmentVersion().toString(),available,available?null:"当前配送不能改派，请核对状态、售后和剩余物料");
    }

    private void requireDeliveryManager(Order order,Long operatorId) {
        Staff operator=operatorId==null?null:staffRepository.findIdentityById(operatorId).orElse(null);
        if(operator==null||!operator.isActive()||operator.hasBeenDeleted()||!java.util.Objects.equals(operator.getStoreId(),order.getStoreId()))throw new IllegalArgumentException("需要本店有效员工身份改派");
    }

    /** 明确改派与原签收争用同一父订单；不改变原发货重放语义。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reassignDelivery(Long orderId,Long expectedVersion,Long targetStaffId,String reason,Long operatorId) {
        if(orderId==null||expectedVersion==null||expectedVersion<0||targetStaffId==null||!StringUtils.hasText(reason)||reason.trim().length()>200)
            throw new IllegalArgumentException("请提供订单、原指派版本、配送员及200字以内改派原因");
        Order order=orderRepository.findByIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("配送订单不存在"));
        OrderDelivery delivery=deliveryRepository.findByOrderId(orderId).orElseThrow(()->new IllegalArgumentException("订单没有配送记录"));
        requireDeliveryManager(order,operatorId);
        if(resolveDeliveryType(delivery)!=2)throw new IllegalArgumentException("只有门店人工配送支持改派");
        validateDeliveryAction(order,delivery,false);
        if(!expectedVersion.equals(delivery.getAssignmentVersion())||expectedVersion==Long.MAX_VALUE)throw new IllegalArgumentException("指派已变化，请重新读取当前配送信息");
        Staff target=staffRepository.findIdentityById(targetStaffId).orElseThrow(()->new IllegalArgumentException("配送员不存在"));
        if(!target.isActive()||target.hasBeenDeleted()||!java.util.Objects.equals(target.getStoreId(),order.getStoreId()))throw new IllegalArgumentException("配送员必须是本店在职员工");
        if(targetStaffId.equals(delivery.getDeliveryStaffId()))throw new IllegalArgumentException("请选择不同的配送员");
        Long previous=delivery.getDeliveryStaffId();
        delivery.setDeliveryStaffId(targetStaffId);delivery.setAssignmentVersion(expectedVersion+1);
        deliveryRepository.save(delivery);
        var log=new com.agri_system.domain.system.log.model.SysOperationLog();
        log.setStaffId(operatorId);log.setModule("订单配送");log.setAction("配送改派");log.setStatus(1);log.setCreatedAt(LocalDateTime.now());
        log.setRequestUri("/api/admin/orders/"+orderId+"/reassign");log.setMethod("POST");
        log.setRequestParams("订单="+orderId+"; 原配送员="+previous+"; 新配送员="+targetStaffId+"; 原版本="+expectedVersion+"; 原因="+reason.trim());
        operationLogRepository.save(log);
    }

    private boolean hasProcessingDeliveryAfterSales(Long orderId) {
        return com.agri_system.domain.order.service.OrderDeliveryEligibility.hasProcessingAfterSales(orderId,afterSalesRepository);
    }

    private String deliveryConfirmationKey(Order order, OrderDelivery delivery,
            List<com.agri_system.domain.order.model.DeliveryItemQuantity> items) {
        return OrderDeliveryConfirmation.key(order,delivery,items);
    }

    private boolean canDeliver(Order order, OrderDelivery delivery, boolean shipping) {
        return com.agri_system.domain.order.service.OrderDeliveryEligibility.canDeliver(order,delivery,shipping,afterSalesRepository,orderRepository);
    }

    private void validateDeliveryAction(Order order, OrderDelivery delivery, boolean shipping) {
        if (!canDeliver(order, delivery, shipping)) {
            throw new IllegalArgumentException("订单当前不能交付：请核对配送状态、处理中售后及剩余实出库物料");
        }
    }



    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.agri_system.application.order.dto.vo.OrderRefundPreviewVO previewRefund(Long orderId,
            com.agri_system.application.order.dto.command.OrderRefundPreviewCommand command) {
        return previewRefund(orderId, command, false);
    }

    /** 批准前核对原成交和原资金，只计算不执行退款，不先回库。 */
    public void validateAfterSalesApproval(Order order, com.agri_system.domain.after_sales.model.AfterSales applied) {
        var request=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand();
        request.setAfterSalesId(applied.getId());
        var result=previewRefund(order.getId(),request,true);
        if(result.refundAmount().compareTo(applied.getRefundAmount())!=0) throw new IllegalArgumentException("售后金额与核准物料不一致，请核对原单");
    }

    private com.agri_system.application.order.dto.vo.OrderRefundPreviewVO previewRefund(Long orderId,
            com.agri_system.application.order.dto.command.OrderRefundPreviewCommand command, boolean forApproval) {
        Order order=orderRepository.findByIdForUpdate(orderId).orElseThrow(()->new IllegalArgumentException("订单不存在"));
        if(command==null) command=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand();
        Long excluded=command.getAfterSalesId();
        var processing=afterSalesRepository.findProcessingByOrderId(orderId);
        if(processing.isPresent() && !java.util.Objects.equals(processing.get().getId(),excluded)) throw new IllegalArgumentException("订单已有处理中售后，请先完成或取消");
        com.agri_system.domain.after_sales.model.AfterSales approved=null;
        if(excluded!=null) {
            approved=afterSalesRepository.findByIdForUpdate(excluded).orElseThrow(()->new IllegalArgumentException("售后单不存在"));
            if(!java.util.Objects.equals(approved.getOrderId(),orderId) || !(forApproval ? approved.isPending() : approved.isWaitRefund())) throw new IllegalArgumentException("只能预览原订单待退款售后");
        } else if(order.getOrderStatus()==null || !java.util.Set.of(2,3,4,5,6,8,9).contains(order.getOrderStatus())) {
            throw new IllegalArgumentException("订单当前状态不支持退货预览");
        }
        OrderRefundCommand request=new OrderRefundCommand();request.setOrderId(orderId);
        List<com.agri_system.application.order.dto.command.OrderRefundPreviewCommand.Item> requested=command.getItems();
        if(approved!=null) {
            var approvedItems=afterSalesItemRepository.findByAfterSalesId(excluded);
            if(approvedItems.isEmpty()) throw new IllegalArgumentException("售后缺少核准物料明细");
            if(approved.getRefundAmount()==null || approvedItems.stream().anyMatch(i->i.getRefundAmount()==null)
                    || approvedItems.stream().map(com.agri_system.domain.after_sales.model.AfterSalesItem::getRefundAmount)
                    .reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(approved.getRefundAmount())!=0) {
                throw new IllegalArgumentException("售后金额与核准物料不一致，请核对原单");
            }
            if(requested==null || requested.isEmpty()) {
                requested=approvedItems.stream().map(item->{var row=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand.Item();row.setOrderItemId(item.getOrderItemId());row.setQuantity(item.getQuantity());row.setRefundAmount(item.getRefundAmount());return row;}).toList();
            }
            var original=approvedItems.stream().collect(Collectors.toMap(com.agri_system.domain.after_sales.model.AfterSalesItem::getOrderItemId,i->i));
            if(requested.size()!=original.size()) throw new IllegalArgumentException("售后退款必须与核准物料一致");
            for(var row:requested) {
                var item=original.get(row.getOrderItemId());
                if(item==null || row.getQuantity()==null || row.getQuantity().compareTo(item.getQuantity())!=0) throw new IllegalArgumentException("售后退款物料及数量与核准不一致");
                if(row.getRefundAmount()==null) row.setRefundAmount(item.getRefundAmount());
                if(row.getRefundAmount().compareTo(item.getRefundAmount())>0) throw new IllegalArgumentException("成交冲销不能超过核准金额");
            }
        }
        if(requested!=null && !requested.isEmpty()) request.setItems(requested.stream().map(row->{var item=new OrderRefundCommand.RefundItem();item.setOrderItemId(row.getOrderItemId());item.setQuantity(row.getQuantity());item.setRefundAmount(row.getRefundAmount());return item;}).toList());
        List<OrderItem> goods=itemRepository.findByOrderId(orderId);
        RefundPlan plan=buildRefundPlan(order,goods,request,excluded);
        if(approved!=null && plan.refundAmount().compareTo(approved.getRefundAmount())>0) throw new IllegalArgumentException("成交冲销不能超过售后核准金额");
        var funding=paymentService.previewRefund(order,plan.refundAmount());
        Map<Long,RefundAggregate> history=loadOrderItemRefundAggregates(orderId,excluded);
        Map<Long,BigDecimal> net=RefundAmountPolicy.refundableBaseAmounts(order,goods);
        StringBuilder fingerprint=new StringBuilder(funding.state()).append("|EX:").append(excluded);
        if(approved!=null) fingerprint.append(':').append(approved.getStatus()).append(':').append(approved.getRefundAmount());
        for(OrderItem item:goods.stream().sorted(java.util.Comparator.comparing(OrderItem::getId)).toList()) {
            var past=history.get(item.getId());
            fingerprint.append("|G:").append(item.getId()).append(':').append(item.getQuantity()).append(':').append(net.get(item.getId())).append(':')
                    .append(past==null?BigDecimal.ZERO:past.quantity).append(':').append(past==null?BigDecimal.ZERO:past.amount);
        }
        var rows=plan.refundItems().stream().sorted(java.util.Comparator.comparing(row->row.orderItem().getId())).map(row->{
            var item=row.orderItem();var past=history.get(item.getId());
            fingerprint.append("|R:").append(item.getId()).append(':').append(row.quantity().stripTrailingZeros().toPlainString()).append(':').append(row.refundAmount().setScale(2));
            return new com.agri_system.application.order.dto.vo.OrderRefundPreviewVO.Item(item.getId(),row.quantity(),row.refundAmount(),
                    item.getQuantity().subtract(past==null?BigDecimal.ZERO:past.quantity),net.get(item.getId()).subtract(past==null?BigDecimal.ZERO:past.amount));
        }).toList();
        return new com.agri_system.application.order.dto.vo.OrderRefundPreviewVO(refundHash(fingerprint.toString()),orderId,plan.refundAmount(),funding.creditReductionAmount(),funding.cashRefundAmount(),funding.remainingDebt(),funding.remainingRefundableAmount(),rows,funding.sources());
    }

    /** 所有正式退款都必须先确认当前资金、金额和实退数量。 */
    public void validateRefundQuote(Order order,com.agri_system.application.order.dto.command.OrderRefundPreviewCommand request,BigDecimal amount,String quoteId) {
        RefundRequestIdentity.requireQuote(quoteId);
        var current=previewRefund(order.getId(),request);
        if(!quoteId.equals(current.quoteId()) || amount.compareTo(current.refundAmount())!=0) throw new IllegalArgumentException("退货资金预览已变化，请重新确认");
    }
    private String refundHash(String text) {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private com.agri_system.application.order.dto.command.OrderRefundPreviewCommand refundRequest(OrderRefundCommand command) {
        var request=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand();
        if(command.getItems()!=null) request.setItems(command.getItems().stream().map(row->{var i=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand.Item();i.setOrderItemId(row.getOrderItemId());i.setQuantity(row.getQuantity());i.setRefundAmount(row.getRefundAmount());return i;}).toList());
        return request;
    }

    /**
     * 退款
     * 
     * @param command 退款命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void refundOrder(OrderRefundCommand command, Long createdBy) {
        Order order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        String quoteId=RefundRequestIdentity.requireQuote(command.getQuoteId());
        String requestHash=RefundRequestIdentity.hash(command,createdBy);
        var original=afterSalesRepository.findByRefundQuoteIdForUpdate(quoteId);
        if(original.isPresent()) {
            RefundRequestIdentity.validateCompleted(original.get(),order.getId(),createdBy,quoteId,requestHash);
            return;
        }
        if (!order.canRefund()) throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 订单不支持退款");
        if (afterSalesRepository.findProcessingByOrderId(order.getId()).isPresent()) {
            throw new IllegalArgumentException("订单已有处理中售后，请先完成或取消该售后");
        }
        List<OrderItem> orderItems = itemRepository.findByOrderId(order.getId());
        RefundPlan refundPlan = buildRefundPlan(order, orderItems, command);
        validateRefundQuote(order,refundRequest(command),refundPlan.refundAmount(),command.getQuoteId());
        OrderPaymentService.RefundResult funding=paymentService.refund(order, refundPlan.refundAmount(), createdBy);
        restockOrderItemsForRefund(order, refundPlan.refundItems(), createdBy);
        Long refundFactId = saveDirectRefundFacts(order, refundPlan, funding, command.getRefundReason(), createdBy, quoteId, requestHash);
        if (refundPlan.fullRefund()) order.markAsRefunded(); else order.markAsPartRefunded();
        orderRepository.save(order);
        paymentService.restoreCouponIfFullyReturned(order);
        eventPublisher.publishEvent(new OrderRefundedEvent(order.getId(), refundPlan.refundAmount(),
                extractRefundItemQuantities(refundPlan.refundItems()), "AFTER_SALES:" + refundFactId));
    }

    /** 直退和售后共享既有退款事实；没有另一套可写退款来源。 */
    private Long saveDirectRefundFacts(Order order, RefundPlan plan, OrderPaymentService.RefundResult funding, String reason, Long operator, String quoteId, String requestHash) {
        var fact = new com.agri_system.domain.after_sales.model.AfterSales();
        fact.setAfterSalesNo("ASD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        fact.setRefundQuoteId(quoteId); fact.setRefundRequestHash(requestHash);
        fact.setOrderId(order.getId()); fact.setCustomerId(order.getCustomerId()); fact.setStoreId(order.getStoreId());
        fact.setType(com.agri_system.domain.after_sales.model.AfterSales.TYPE_ADMIN_RETURN);
        fact.setStatus(com.agri_system.domain.after_sales.model.AfterSales.STATUS_FINISHED);
        fact.setRefundAmount(plan.refundAmount()); fact.setCreditReductionAmount(funding.creditReductionAmount());fact.setCashRefundAmount(funding.cashRefundAmount()); fact.setReason(reason); fact.setAuditBy(operator);
        fact.setRefundBy(operator); fact.setAuditTime(LocalDateTime.now()); fact.setRefundTime(LocalDateTime.now());
        fact.setCreatedAt(LocalDateTime.now()); fact.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(fact);
        List<com.agri_system.domain.after_sales.model.AfterSalesItem> details = new ArrayList<>();
        for (RefundOrderItem item : plan.refundItems()) {
            var detail = new com.agri_system.domain.after_sales.model.AfterSalesItem();
            detail.setAfterSalesId(fact.getId()); detail.setOrderItemId(item.orderItem().getId());
            detail.setProductId(item.orderItem().getProductId()); detail.setQuantity(item.quantity());
            detail.setRefundAmount(item.refundAmount()); detail.setCreatedAt(LocalDateTime.now()); details.add(detail);
        }
        afterSalesItemRepository.saveBatch(details);
        return fact.getId();
    }

    // ==================== 私有方法 ====================
    
    /**
     * ⛔️ 保存支付流水记录（Critical Fix）
     * 
     * 主支付方式输入：
     * 1 - 现金
     * 2 - 微信
     * 3 - 支付宝
     * 4 - 余额
     * 6 - 赊销
     * 10 - 混合支付
     *
     * 支付流水编码：
     * 1 - 现金
     * 2 - 微信
     * 3 - 支付宝
     * 4 - 本金余额
     * 5 - 优惠券
     * 6 - 赊销
     * 8 - 专用金余额
     * 
     * 关键修复：
     * - 赊销订单只记录一条 payment_type=6 的记录
     * - 余额与专用金拆成独立流水，不再复用支付宝编码
     * 
     * @param order 订单
     * @param command POS订单创建命令
     * @param createdBy 操作人ID
     */
}
