package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.after_sales.dto.command.ClientApplyCommand;
import com.agri_system.application.after_sales.dto.query.AfterSalesPageQuery;
import com.agri_system.application.after_sales.dto.vo.AfterSalesDetailVO;
import com.agri_system.application.after_sales.dto.vo.AfterSalesItemVO;
import com.agri_system.application.after_sales.dto.vo.AfterSalesPageVO;
import com.agri_system.application.after_sales.exception.AfterSalesErrorCode;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.application.order.service.RefundAmountPolicy;
import com.agri_system.application.order.service.RefundRequestIdentity;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 售后应用服务
 */
@Service
@RequiredArgsConstructor
public class AfterSalesApplicationService {

    private static final String SOURCE_TYPE = "SALES_RETURN";

    private final AfterSalesRepository afterSalesRepository;
    private final AfterSalesItemRepository afterSalesItemRepository;
    private final AfterSalesDomainService afterSalesDomainService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;
    private final InventoryApplicationService inventoryApplicationService;
    private final OrderPaymentService orderPaymentService;
    private final com.agri_system.application.order.service.OrderApplicationService orderApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 小程序发起售后（未发货取消）
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long applyFromClient(ClientApplyCommand command, Long customerId) {
        Order order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));

        if (order.getCustomerId() == null || !order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 无权操作该订单");
        }

        if (!isClientCancelable(order.getOrderStatus())) {
            throw new IllegalArgumentException(AfterSalesErrorCode.ORDER_STATUS_INVALID + ": 订单状态不支持售后");
        }

        ensureNoProcessingAfterSales(order.getId());

        AfterSales afterSales = new AfterSales();
        afterSales.setAfterSalesNo(afterSalesDomainService.generateAfterSalesNo());
        afterSales.setOrderId(order.getId());
        afterSales.setCustomerId(order.getCustomerId());
        afterSales.setStoreId(order.getStoreId());
        afterSales.setType(AfterSales.TYPE_CLIENT_CANCEL);
        afterSales.setStatus(AfterSales.STATUS_APPLIED);
        afterSales.setRefundAmount(order.getPayAmount());
        afterSales.setReason(command.getReason());
        afterSales.setCreatedAt(LocalDateTime.now());
        afterSales.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(afterSales);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        Map<Long,BigDecimal> netAmounts=RefundAmountPolicy.refundableBaseAmounts(order,orderItems);
        List<AfterSalesItem> items = orderItems.stream()
                .map(item -> {
                    AfterSalesItem afterSalesItem = new AfterSalesItem();
                    afterSalesItem.setAfterSalesId(afterSales.getId());
                    afterSalesItem.setOrderItemId(item.getId());
                    afterSalesItem.setProductId(item.getProductId());
                    afterSalesItem.setQuantity(item.getQuantity());
                    afterSalesItem.setRefundAmount(netAmounts.get(item.getId()));
                    afterSalesItem.setCreatedAt(LocalDateTime.now());
                    return afterSalesItem;
                })
                .collect(Collectors.toList());
        afterSalesItemRepository.saveBatch(items);

        return afterSales.getId();
    }

    /**
     * 小程序撤销售后
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void cancel(Long afterSalesId, Long customerId) {
        AfterSales afterSales = afterSalesRepository.findById(afterSalesId)
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));

        orderRepository.findByIdForUpdate(afterSales.getOrderId()).orElseThrow(()->new IllegalArgumentException("订单不存在"));
        afterSales=afterSalesRepository.findByIdForUpdate(afterSalesId).orElseThrow(()->new IllegalArgumentException("售后单不存在"));
        if (!Objects.equals(afterSales.getCustomerId(), customerId)) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 无权撤销该售后");
        }
        if (!afterSales.isPending()) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 当前状态不允许撤销");
        }

        afterSales.setStatus(AfterSales.STATUS_REJECTED);
        afterSales.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(afterSales);
    }

    /**
     * 管理端创建退货售后（自动审核并自动退款）
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long createFromAdmin(AdminReturnCommand command, Long operatorId) {
        Order order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));

        String quoteId=RefundRequestIdentity.requireQuote(command.getQuoteId());
        String requestHash=RefundRequestIdentity.hash(command,operatorId);
        var original=afterSalesRepository.findByRefundQuoteIdForUpdate(quoteId);
        if(original.isPresent()) {
            RefundRequestIdentity.validateCompleted(original.get(),order.getId(),operatorId,quoteId,requestHash);
            return original.get().getId();
        }
        boolean zeroGiftAfterPaidRefund = Integer.valueOf(8).equals(order.getOrderStatus())
                && command.getRefundAmount() != null && command.getRefundAmount().signum() == 0;
        if (!isAdminReturnable(order.getOrderStatus()) && !zeroGiftAfterPaidRefund) {
            throw new IllegalArgumentException(AfterSalesErrorCode.ORDER_STATUS_INVALID + ": 订单状态必须为已发货或已完成");
        }

        ensureNoProcessingAfterSales(order.getId());

        RefundAmountPolicy.validateOrderRefund(order.getPayAmount(),
                resolveCumulativeRefundAmount(order.getId(), null, BigDecimal.ZERO), command.getRefundAmount());

        AfterSales afterSales = new AfterSales();
        afterSales.setAfterSalesNo(afterSalesDomainService.generateAfterSalesNo());
        afterSales.setOrderId(order.getId());
        afterSales.setCustomerId(order.getCustomerId());
        afterSales.setStoreId(order.getStoreId());
        afterSales.setRefundQuoteId(quoteId);
        afterSales.setRefundRequestHash(requestHash);
        afterSales.setType(AfterSales.TYPE_ADMIN_RETURN);
        afterSales.setStatus(AfterSales.STATUS_WAIT_REFUND);
        afterSales.setRefundAmount(command.getRefundAmount());
        afterSales.setReason(command.getReason());
        afterSales.setAuditRemark("线下退货自动审核");
        afterSales.setAuditBy(operatorId);
        afterSales.setAuditTime(LocalDateTime.now());
        afterSales.setCreatedAt(LocalDateTime.now());
        afterSales.setUpdatedAt(LocalDateTime.now());
        Map<Long, OrderItem> orderItemMap = orderItemRepository.findByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(OrderItem::getId, oi -> oi));
        Map<Long, BigDecimal> refundBases = RefundAmountPolicy.refundableBaseAmounts(order, List.copyOf(orderItemMap.values()));
        Map<Long, RefundAggregate> existingRefunds = loadExistingRefundAggregates(order.getId());

        List<AfterSalesItem> items = buildAdminItems(afterSales, command, orderItemMap, refundBases, existingRefunds);
        if (command.getRefundAmount().signum() == 0 && items.stream().anyMatch(item -> {
            OrderItem material = orderItemMap.get(item.getOrderItemId());
            RefundAggregate history = existingRefunds.get(material.getId());
            return RefundAmountPolicy.maximumForReturn(material,
                    history == null ? BigDecimal.ZERO : history.quantity,
                    history == null ? BigDecimal.ZERO : history.refundAmount,
                    item.getQuantity(), refundBases.get(material.getId())).signum() != 0;
        })) {
            throw new IllegalArgumentException("0元退款仅适用于本次没有现金可退的套餐物料");
        }
        var previewRequest=previewRequest(items,null);
        orderApplicationService.validateRefundQuote(order,previewRequest,command.getRefundAmount(),command.getQuoteId());
        // 必须先客户/账户/来源资金锁，再库存锁及任何实物回库。
        var funding=orderPaymentService.refund(order,command.getRefundAmount(),operatorId);
        inventoryApplicationService.lockProducts(order.getStoreId(),items.stream().map(AfterSalesItem::getProductId).toList());
        afterSalesRepository.save(afterSales);
        items.forEach(item -> item.setAfterSalesId(afterSales.getId()));
        afterSalesItemRepository.saveBatch(items);

        triggerRestock(afterSales, items, operatorId);
        
        BigDecimal refundAmount=command.getRefundAmount();
        afterSales.setCreditReductionAmount(funding.creditReductionAmount());afterSales.setCashRefundAmount(funding.cashRefundAmount());
        BigDecimal cumulativeRefundAmount=resolveCumulativeRefundAmount(order.getId(),afterSales.getId(),refundAmount);
        if(cumulativeRefundAmount.compareTo(order.getPayAmount())>=0) order.markAsRefunded();
        else if(refundAmount.signum()>0) order.markAsPartRefunded();
        orderRepository.save(order);
        // 0元套餐物料仍须完成售后和回库，不能留下阻塞下一次退货的处理中记录。
        afterSales.markFinished(operatorId);
        afterSales.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(afterSales);
        orderPaymentService.restoreCouponIfFullyReturned(order);
        eventPublisher.publishEvent(new OrderRefundedEvent(order.getId(), refundAmount,
                buildRefundItemQuantityMap(items), "AFTER_SALES:" + afterSales.getId()));
        
        return afterSales.getId();
    }

    /**
     * 审核售后
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void audit(AfterSalesAuditCommand command, Long operatorId) {
        AfterSales afterSales=afterSalesRepository.findById(command.getAfterSalesId()).orElseThrow(()->new IllegalArgumentException("售后单不存在"));
        Order order=orderRepository.findByIdForUpdate(afterSales.getOrderId()).orElseThrow(()->new IllegalArgumentException("订单不存在"));
        afterSales=afterSalesRepository.findByIdForUpdate(command.getAfterSalesId()).orElseThrow(()->new IllegalArgumentException("售后单不存在"));
        if(!afterSales.isPending()) throw new IllegalArgumentException("当前状态不可审核");
        if(Boolean.TRUE.equals(command.getApproved()) && afterSales.getType()==AfterSales.TYPE_CLIENT_CANCEL && !isClientCancelable(order.getOrderStatus())) {
            throw new IllegalArgumentException("原订单已开始交付，不能继续批准取消");
        }
        if (Boolean.TRUE.equals(command.getApproved())) {
            orderApplicationService.validateAfterSalesApproval(order,afterSales);
            afterSales.markWaitRefund(operatorId, command.getRemark());
            afterSales.setUpdatedAt(LocalDateTime.now());
            afterSalesRepository.save(afterSales);

            List<AfterSalesItem> items = afterSalesItemRepository.findByAfterSalesId(afterSales.getId());
            triggerRestock(afterSales, items, operatorId);

            if (afterSales.getType() == AfterSales.TYPE_CLIENT_CANCEL) {
                order.markAsCancelled();
                orderRepository.save(order);
            }
        } else {
            afterSales.markRejected(operatorId, command.getRemark());
            afterSales.setUpdatedAt(LocalDateTime.now());
            afterSalesRepository.save(afterSales);
        }
    }

    /**
     * 确认退款
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void refund(AfterSalesRefundCommand command, Long operatorId) {
        AfterSales afterSales = afterSalesRepository.findById(command.getAfterSalesId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));
        Order order = orderRepository.findByIdForUpdate(afterSales.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        // 等待订单锁期间其它操作可能已经完成退款，取得锁后必须重新确认售后状态。
        afterSales = afterSalesRepository.findByIdForUpdate(command.getAfterSalesId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));
        String quoteId=RefundRequestIdentity.requireQuote(command.getQuoteId());
        String requestHash=RefundRequestIdentity.hash(command,order.getId(),operatorId);
        if(Integer.valueOf(AfterSales.STATUS_FINISHED).equals(afterSales.getStatus())) {
            RefundRequestIdentity.validateCompleted(afterSales,order.getId(),operatorId,quoteId,requestHash);
            return;
        }
        if (!afterSales.isWaitRefund()) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 当前状态不可退款");
        }
        var original=afterSalesRepository.findByRefundQuoteIdForUpdate(quoteId);
        if(original.isPresent() && !Objects.equals(original.get().getId(),afterSales.getId())) {
            throw new IllegalArgumentException("相同退货预览不能用于不同请求");
        }

        BigDecimal refundAmount = command.getRefundAmount() != null ? command.getRefundAmount() : afterSales.getRefundAmount();
        if (refundAmount.compareTo(afterSales.getRefundAmount()) > 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 退款金额不能超过审核金额");
        }
        RefundAmountPolicy.validateOrderRefund(order.getPayAmount(),
                resolveCumulativeRefundAmount(order.getId(), afterSales.getId(), BigDecimal.ZERO), refundAmount);

        List<AfterSalesItem> items=confirmedItems(afterSales,refundAmount);
        orderApplicationService.validateRefundQuote(order,previewRequest(items,afterSales.getId()),refundAmount,command.getQuoteId());
        var funding=orderPaymentService.refund(order,refundAmount,operatorId);
        afterSalesItemRepository.saveBatch(items);
        afterSales.setCreditReductionAmount(funding.creditReductionAmount());afterSales.setCashRefundAmount(funding.cashRefundAmount());

        afterSales.setRefundQuoteId(quoteId);
        afterSales.setRefundRequestHash(requestHash);
        // 更新售后单状态（确保设置退款人和退款时间）
        afterSales.markFinished(operatorId);
        afterSales.setRefundAmount(refundAmount);
        afterSales.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(afterSales);

        BigDecimal cumulativeRefundAmount = resolveCumulativeRefundAmount(order.getId(), afterSales.getId(), refundAmount);
        if (cumulativeRefundAmount.compareTo(order.getPayAmount()) >= 0) {
            order.markAsRefunded();
        } else if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.markAsPartRefunded();
        }
        orderRepository.save(order);
        orderPaymentService.restoreCouponIfFullyReturned(order);
        eventPublisher.publishEvent(new OrderRefundedEvent(
                order.getId(),
                refundAmount,
                buildRefundItemQuantityMap(items),
                "AFTER_SALES:" + afterSales.getId()
        ));
    }

    public Page<AfterSalesPageVO> queryPage(AfterSalesPageQuery query) {
        Page<AfterSales> page = afterSalesRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getKeyword(),
                query.getStoreId(),
                query.getType(),
                query.getStatus(),
                query.getStartTime(),
                query.getEndTime()
        );

        List<AfterSalesPageVO> records = page.getRecords().stream()
                .map(this::buildPageVO)
                .collect(Collectors.toList());

        Page<AfterSalesPageVO> result = new Page<>();
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalRow(page.getTotalRow());
        result.setTotalPage(page.getTotalPage());
        result.setRecords(records);
        return result;
    }

    public AfterSalesDetailVO getDetail(Long afterSalesId) {
        AfterSales afterSales = afterSalesRepository.findById(afterSalesId)
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));
        return buildDetailVO(afterSales);
    }

    public List<AfterSalesDetailVO> getByOrderId(Long orderId) {
        return afterSalesRepository.findByOrderId(orderId).stream()
                .map(this::buildDetailVO)
                .collect(Collectors.toList());
    }

    private com.agri_system.application.order.dto.command.OrderRefundPreviewCommand previewRequest(List<AfterSalesItem> items,Long excluded) {
        var request=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand();request.setAfterSalesId(excluded);
        request.setItems(items.stream().map(row->{var item=new com.agri_system.application.order.dto.command.OrderRefundPreviewCommand.Item();item.setOrderItemId(row.getOrderItemId());item.setQuantity(row.getQuantity());item.setRefundAmount(row.getRefundAmount());return item;}).toList());return request;
    }
    /** 保持原实退数量，确认少退金额按原核准明细权重分到整数分。 */
    private List<AfterSalesItem> confirmedItems(AfterSales afterSales,BigDecimal amount) {
        List<AfterSalesItem> original=afterSalesItemRepository.findByAfterSalesId(afterSales.getId());
        if(original.isEmpty()) throw new IllegalArgumentException("售后缺少核准明细，不能只改父金额");
        BigDecimal approved=original.stream().map(AfterSalesItem::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(approved.compareTo(afterSales.getRefundAmount())!=0) throw new IllegalArgumentException("核准售后父子金额不一致");
        List<AfterSalesItem> copies=original.stream().map(item->{var copy=new AfterSalesItem();org.springframework.beans.BeanUtils.copyProperties(item,copy);return copy;}).toList();
        if(amount.compareTo(approved)==0) return copies;
        if(approved.signum()<=0) throw new IllegalArgumentException("零金额核准不可调整为其他金额");
        long target=amount.movePointRight(2).longValueExact(),assigned=0;
        List<Integer> order=new ArrayList<>();BigDecimal[] remainders=new BigDecimal[copies.size()];
        for(int i=0;i<copies.size();i++) {
            BigDecimal numerator=BigDecimal.valueOf(target).multiply(original.get(i).getRefundAmount());
            long cents=numerator.divide(approved,0,RoundingMode.DOWN).longValueExact();assigned+=cents;
            copies.get(i).setRefundAmount(BigDecimal.valueOf(cents,2));remainders[i]=numerator.remainder(approved);order.add(i);
        }
        order.sort((a,b)->{int result=remainders[b].compareTo(remainders[a]);return result==0?Integer.compare(a,b):result;});
        for(int i=0;i<target-assigned;i++){var item=copies.get(order.get(i));item.setRefundAmount(item.getRefundAmount().add(new BigDecimal("0.01")));}
        return copies;
    }

    private void ensureNoProcessingAfterSales(Long orderId) {
        afterSalesRepository.findProcessingByOrderId(orderId)
                .ifPresent(as -> {
                    throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_EXISTS + ": 已存在处理中售后单");
                });
    }

    private boolean isClientCancelable(Integer status) {
        return status != null && (status == 3 || status == 4);
    }

    private boolean isAdminReturnable(Integer status) {
        return status != null && (status == 5 || status == 6 || status == 9);
    }

    private boolean isCreditOrder(Order order) {
        if (order.getPaymentMethod() == null) {
            return false;
        }
        return order.getPaymentMethod().equals(OrderMainPaymentMethod.CREDIT.getCode());
    }

    private List<AfterSalesItem> buildAdminItems(AfterSales afterSales,
                                                 AdminReturnCommand command,
                                                 Map<Long, OrderItem> orderItemMap,
                                                 Map<Long, BigDecimal> refundBases,
                                                 Map<Long, RefundAggregate> existingRefunds) {
        BigDecimal total = BigDecimal.ZERO;
        List<AfterSalesItem> items = new ArrayList<>();
        Set<Long> requestedIds = new HashSet<>();
        for (AdminReturnCommand.ReturnItem itemCommand : command.getItems()) {
            if (!requestedIds.add(itemCommand.getOrderItemId())) {
                throw new IllegalArgumentException("同一次退货不能重复提交相同订单明细");
            }
            OrderItem orderItem = Optional.ofNullable(orderItemMap.get(itemCommand.getOrderItemId()))
                    .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单明细不存在"));
            validateAdminReturnItem(orderItem, itemCommand, existingRefunds.get(orderItem.getId()), refundBases.get(orderItem.getId()));
            AfterSalesItem afterSalesItem = new AfterSalesItem();
            afterSalesItem.setAfterSalesId(afterSales.getId());
            afterSalesItem.setOrderItemId(orderItem.getId());
            afterSalesItem.setProductId(orderItem.getProductId());
            afterSalesItem.setQuantity(itemCommand.getQuantity());
            afterSalesItem.setRefundAmount(itemCommand.getRefundAmount());
            afterSalesItem.setCreatedAt(LocalDateTime.now());
            items.add(afterSalesItem);
            total = total.add(itemCommand.getRefundAmount());
        }

        if (total.compareTo(command.getRefundAmount()) != 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 明细退款金额与总金额不一致");
        }
        return items;
    }

    private Map<Long, RefundAggregate> loadExistingRefundAggregates(Long orderId) {
        Map<Long, RefundAggregate> aggregates = new HashMap<>();
        List<AfterSales> history = afterSalesRepository.findByOrderId(orderId);
        if (history == null || history.isEmpty()) {
            return aggregates;
        }

        for (AfterSales historyItem : history) {
            if (historyItem == null || historyItem.getStatus() == AfterSales.STATUS_REJECTED) {
                continue;
            }
            List<AfterSalesItem> items = afterSalesItemRepository.findByAfterSalesId(historyItem.getId());
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (AfterSalesItem item : items) {
                if (item == null || item.getOrderItemId() == null) {
                    continue;
                }
                RefundAggregate aggregate = aggregates.computeIfAbsent(item.getOrderItemId(), key -> new RefundAggregate());
                aggregate.quantity = aggregate.quantity.add(defaultAmount(item.getQuantity()));
                aggregate.refundAmount = aggregate.refundAmount.add(defaultAmount(item.getRefundAmount()));
            }
        }
        return aggregates;
    }

    private BigDecimal resolveCumulativeRefundAmount(Long orderId, Long currentAfterSalesId, BigDecimal currentRefundAmount) {
        BigDecimal total = defaultAmount(currentRefundAmount);
        List<AfterSales> history = afterSalesRepository.findByOrderId(orderId);
        if (history == null || history.isEmpty()) {
            return total;
        }
        for (AfterSales historyItem : history) {
            if (historyItem == null
                    || historyItem.getStatus() != AfterSales.STATUS_FINISHED
                    || Objects.equals(historyItem.getId(), currentAfterSalesId)) {
                continue;
            }
            total = total.add(defaultAmount(historyItem.getRefundAmount()));
        }
        return total;
    }

    private void validateAdminReturnItem(OrderItem orderItem,
                                         AdminReturnCommand.ReturnItem itemCommand,
                                         RefundAggregate existingRefund,
                                         BigDecimal netLineTotal) {
        BigDecimal requestedQuantity = defaultAmount(itemCommand.getQuantity());
        BigDecimal requestedRefundAmount = defaultAmount(itemCommand.getRefundAmount());
        BigDecimal existingQuantity = existingRefund != null ? existingRefund.quantity : BigDecimal.ZERO;
        BigDecimal existingRefundAmount = existingRefund != null ? existingRefund.refundAmount : BigDecimal.ZERO;
        if (requestedQuantity.signum() <= 0 || requestedRefundAmount.signum() < 0 || requestedRefundAmount.scale() > 2) {
            throw new IllegalArgumentException("退货数量必须大于0，退款金额不能为负且最多两位小数");
        }

        if (requestedQuantity.compareTo(orderItem.getQuantity()) > 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 退货数量不能超过购买数量");
        }
        if (existingQuantity.add(requestedQuantity).compareTo(orderItem.getQuantity()) > 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 该商品累计退货数量已超过原购买数量");
        }

        BigDecimal itemTotalPrice = defaultAmount(orderItem.getTotalPrice());
        if (itemTotalPrice.compareTo(BigDecimal.ZERO) <= 0
                && orderItem.getUnitPrice() != null
                && orderItem.getQuantity() != null) {
            itemTotalPrice = orderItem.getUnitPrice().multiply(orderItem.getQuantity());
        }
        if (requestedRefundAmount.compareTo(itemTotalPrice) > 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 明细退款金额不能超过商品成交金额");
        }
        if (existingRefundAmount.add(requestedRefundAmount).compareTo(itemTotalPrice) > 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 该商品累计退款金额已超过商品成交金额");
        }
        BigDecimal maximum = RefundAmountPolicy.maximumForReturn(orderItem, existingQuantity,
                existingRefundAmount, requestedQuantity, netLineTotal);
        inventoryApplicationService.validateReturnQuantity(orderItem, requestedQuantity);
        if (requestedRefundAmount.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("套餐明细退款金额不能超过累计退货数量对应的成交金额");
        }
    }

    private void triggerRestock(AfterSales afterSales, List<AfterSalesItem> items, Long operatorId) {
        Order order = orderRepository.findById(afterSales.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        Map<Long, OrderItem> orderItemMap = orderItemRepository.findByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(OrderItem::getId, oi -> oi));

        for (AfterSalesItem item : items) {
            OrderItem orderItem = Optional.ofNullable(orderItemMap.get(item.getOrderItemId()))
                    .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单明细不存在"));
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("商品不存在"));

            InventoryInCommand inCommand = new InventoryInCommand();
            inCommand.setStoreId(order.getStoreId());
            inCommand.setProductId(item.getProductId());
            inCommand.setBatchNo(afterSales.getAfterSalesNo() + "-" + item.getOrderItemId());
            inCommand.setQuantity(item.getQuantity());
            inCommand.setUnit(orderItem.getUnit());
            BigDecimal costPrice = product.getAvgCostPrice();
            if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
                costPrice = orderItem.getUnitPrice();
            }
            inCommand.setCostPrice(costPrice);
            inCommand.setSourceType(SOURCE_TYPE);
            inCommand.setSourceId(afterSales.getId());
            inCommand.setSourceLineNo(Math.toIntExact(item.getOrderItemId()));
            inventoryApplicationService.inbound(inCommand, operatorId);
        }
    }

    private BigDecimal resolveRestockQuantity(AfterSalesItem item, OrderItem orderItem, Product product) {
        if (item.getQuantity() == null) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 售后明细数量不能为空");
        }
        if (!StringUtils.hasText(orderItem.getUnit())
                || !StringUtils.hasText(product.getAuxUnit())
                || !Objects.equals(orderItem.getUnit(), product.getAuxUnit())
                || product.getMainToAuxRatio() == null
                || product.getMainToAuxRatio().compareTo(BigDecimal.ONE) <= 0) {
            return item.getQuantity();
        }
        return item.getQuantity().divide(product.getMainToAuxRatio(), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Map<Long, BigDecimal> buildRefundItemQuantityMap(List<AfterSalesItem> items) {
        Map<Long, BigDecimal> refundItems = new HashMap<>();
        if (items == null || items.isEmpty()) {
            return refundItems;
        }
        for (AfterSalesItem item : items) {
            if (item == null || item.getOrderItemId() == null || item.getQuantity() == null) {
                continue;
            }
            refundItems.merge(item.getOrderItemId(), item.getQuantity(), BigDecimal::add);
        }
        return refundItems;
    }

    private AfterSalesPageVO buildPageVO(AfterSales afterSales) {
        AfterSalesPageVO vo = new AfterSalesPageVO();
        vo.setId(afterSales.getId());
        vo.setAfterSalesNo(afterSales.getAfterSalesNo());
        vo.setOrderId(afterSales.getOrderId());
        vo.setCustomerId(afterSales.getCustomerId());
        vo.setStoreId(afterSales.getStoreId());
        vo.setType(afterSales.getType());
        vo.setStatus(afterSales.getStatus());
        vo.setRefundAmount(afterSales.getRefundAmount());
        vo.setCreditReductionAmount(afterSales.getCreditReductionAmount());vo.setCashRefundAmount(afterSales.getCashRefundAmount());
        vo.setReason(afterSales.getReason());
        vo.setAuditRemark(afterSales.getAuditRemark());
        vo.setAuditBy(afterSales.getAuditBy());
        vo.setAuditTime(afterSales.getAuditTime());
        vo.setRefundBy(afterSales.getRefundBy());
        vo.setRefundTime(afterSales.getRefundTime());
        vo.setCreatedAt(afterSales.getCreatedAt());

        if (afterSales.getOrderId() != null) {
            orderRepository.findById(afterSales.getOrderId()).ifPresent(order -> {
                vo.setOrderNo(order.getOrderNo());
                vo.setOrderStatus(order.getOrderStatus());
            });
        }
        if (afterSales.getCustomerId() != null) {
            customerRepository.findByIdIncludingDeleted(afterSales.getCustomerId()).ifPresent(customer -> {
                vo.setCustomerName(customer.getName());
                vo.setCustomerPhone(customer.getPhone());
            });
        }
        if (afterSales.getStoreId() != null) {
            storeRepository.findByIdIncludingDeleted(afterSales.getStoreId()).ifPresent(store -> vo.setStoreName(store.getName()));
        }
        if (afterSales.getAuditBy() != null) {
            staffRepository.findById(afterSales.getAuditBy()).ifPresent(staff -> vo.setAuditByName(staff.getName()));
        }
        if (afterSales.getRefundBy() != null) {
            staffRepository.findById(afterSales.getRefundBy()).ifPresent(staff -> vo.setRefundByName(staff.getName()));
        }
        return vo;
    }

    private AfterSalesDetailVO buildDetailVO(AfterSales afterSales) {
        AfterSalesDetailVO vo = new AfterSalesDetailVO();
        AfterSalesPageVO base = buildPageVO(afterSales);
        org.springframework.beans.BeanUtils.copyProperties(base, vo);
        vo.setItems(afterSalesItemRepository.findByAfterSalesId(afterSales.getId()).stream()
                .map(this::buildItemVO)
                .collect(Collectors.toList()));
        return vo;
    }

    private AfterSalesItemVO buildItemVO(AfterSalesItem item) {
        AfterSalesItemVO vo = new AfterSalesItemVO();
        vo.setId(item.getId());
        vo.setOrderItemId(item.getOrderItemId());
        vo.setProductId(item.getProductId());
        vo.setQuantity(item.getQuantity());
        vo.setRefundAmount(item.getRefundAmount());

        OrderItem orderItem = item.getOrderItemId() != null
                ? orderItemRepository.findById(item.getOrderItemId()).orElse(null)
                : null;
        if (orderItem != null) {
            vo.setProductName(orderItem.getProductName());
            vo.setUnit(orderItem.getUnit());
        }
        return vo;
    }

    private static class RefundAggregate {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal refundAmount = BigDecimal.ZERO;
    }
}
