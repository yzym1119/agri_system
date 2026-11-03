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

/** 原订单退款预览、审批核对与正式退货编排；不改变原钱货事实和锁序。 */
@Service
@RequiredArgsConstructor
public class OrderRefundService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderPaymentService paymentService;
    private final InventoryApplicationService inventoryService;
    private final ProductRepository productRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final AfterSalesItemRepository afterSalesItemRepository;
    private final ApplicationEventPublisher eventPublisher;

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
