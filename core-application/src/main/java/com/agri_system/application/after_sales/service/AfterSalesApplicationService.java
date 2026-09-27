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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 小程序发起售后（未发货取消）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long applyFromClient(ClientApplyCommand command, Long customerId) {
        Order order = orderRepository.findById(command.getOrderId())
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
        List<AfterSalesItem> items = orderItems.stream()
                .map(item -> {
                    AfterSalesItem afterSalesItem = new AfterSalesItem();
                    afterSalesItem.setAfterSalesId(afterSales.getId());
                    afterSalesItem.setOrderItemId(item.getId());
                    afterSalesItem.setProductId(item.getProductId());
                    afterSalesItem.setQuantity(item.getQuantity());
                    afterSalesItem.setRefundAmount(item.getTotalPrice());
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
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long afterSalesId, Long customerId) {
        AfterSales afterSales = afterSalesRepository.findById(afterSalesId)
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));

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
    @Transactional(rollbackFor = Exception.class)
    public Long createFromAdmin(AdminReturnCommand command, Long operatorId) {
        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));

        if (!isAdminReturnable(order.getOrderStatus())) {
            throw new IllegalArgumentException(AfterSalesErrorCode.ORDER_STATUS_INVALID + ": 订单状态必须为已发货或已完成");
        }
        if (isCreditOrder(order)) {
            throw new IllegalArgumentException(AfterSalesErrorCode.CREDIT_ORDER_NOT_SUPPORTED + ": 赊销订单请走线下冲账流程");
        }

        ensureNoProcessingAfterSales(order.getId());

        AfterSales afterSales = new AfterSales();
        afterSales.setAfterSalesNo(afterSalesDomainService.generateAfterSalesNo());
        afterSales.setOrderId(order.getId());
        afterSales.setCustomerId(order.getCustomerId());
        afterSales.setStoreId(order.getStoreId());
        afterSales.setType(AfterSales.TYPE_ADMIN_RETURN);
        afterSales.setStatus(AfterSales.STATUS_WAIT_REFUND);
        afterSales.setRefundAmount(command.getRefundAmount());
        afterSales.setReason(command.getReason());
        afterSales.setAuditRemark("线下退货自动审核");
        afterSales.setAuditBy(operatorId);
        afterSales.setAuditTime(LocalDateTime.now());
        afterSales.setCreatedAt(LocalDateTime.now());
        afterSales.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(afterSales);

        Map<Long, OrderItem> orderItemMap = orderItemRepository.findByOrderId(order.getId()).stream()
                .collect(Collectors.toMap(OrderItem::getId, oi -> oi));

        List<AfterSalesItem> items = buildAdminItems(afterSales, command, orderItemMap);
        afterSalesItemRepository.saveBatch(items);

        triggerRestock(afterSales, items, operatorId);
        
        // 自动执行退款（线下退货自动完成退款流程）
        BigDecimal refundAmount = command.getRefundAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            // 调用订单模块执行原路退款
            orderPaymentService.refund(order, refundAmount, operatorId);
            
            // 更新售后单状态为已完成，并设置退款人和退款时间
            afterSales.markFinished(operatorId);
            afterSales.setUpdatedAt(LocalDateTime.now());
            afterSalesRepository.save(afterSales);
            
            // 按历史累计退款金额判断最终订单状态
            BigDecimal cumulativeRefundAmount = resolveCumulativeRefundAmount(order.getId(), afterSales.getId(), refundAmount);
            if (cumulativeRefundAmount.compareTo(order.getPayAmount()) >= 0) {
                order.markAsRefunded();
            } else {
                order.markAsPartRefunded();
            }
            orderRepository.save(order);
            eventPublisher.publishEvent(new OrderRefundedEvent(
                    order.getId(),
                    refundAmount,
                    buildRefundItemQuantityMap(items),
                    "AFTER_SALES:" + afterSales.getId()
            ));
        }
        
        return afterSales.getId();
    }

    /**
     * 审核售后
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(AfterSalesAuditCommand command, Long operatorId) {
        AfterSales afterSales = afterSalesRepository.findById(command.getAfterSalesId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));
        if (!afterSales.isPending()) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 当前状态不可审核");
        }
        Order order = orderRepository.findById(afterSales.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));

        if (Boolean.TRUE.equals(command.getApproved())) {
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
    @Transactional(rollbackFor = Exception.class)
    public void refund(AfterSalesRefundCommand command, Long operatorId) {
        AfterSales afterSales = afterSalesRepository.findById(command.getAfterSalesId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_NOT_FOUND + ": 售后单不存在"));
        if (!afterSales.isWaitRefund()) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 当前状态不可退款");
        }

        Order order = orderRepository.findById(afterSales.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));

        BigDecimal refundAmount = command.getRefundAmount() != null ? command.getRefundAmount() : afterSales.getRefundAmount();
        if (refundAmount.compareTo(afterSales.getRefundAmount()) > 0) {
            throw new IllegalArgumentException(AfterSalesErrorCode.AFTER_SALES_STATUS_INVALID + ": 退款金额不能超过审核金额");
        }

        // 调用订单模块执行原路退款
        orderPaymentService.refund(order, refundAmount, operatorId);

        // 更新售后单状态（确保设置退款人和退款时间）
        afterSales.markFinished(operatorId);
        afterSales.setUpdatedAt(LocalDateTime.now());
        afterSalesRepository.save(afterSales);

        BigDecimal cumulativeRefundAmount = resolveCumulativeRefundAmount(order.getId(), afterSales.getId(), refundAmount);
        if (cumulativeRefundAmount.compareTo(order.getPayAmount()) >= 0) {
            order.markAsRefunded();
        } else if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.markAsPartRefunded();
        }
        orderRepository.save(order);
        List<AfterSalesItem> items = afterSalesItemRepository.findByAfterSalesId(afterSales.getId());
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
                                                 Map<Long, OrderItem> orderItemMap) {
        Map<Long, RefundAggregate> existingRefunds = loadExistingRefundAggregates(afterSales.getOrderId());
        BigDecimal total = BigDecimal.ZERO;
        List<AfterSalesItem> items = new ArrayList<>();
        for (AdminReturnCommand.ReturnItem itemCommand : command.getItems()) {
            OrderItem orderItem = Optional.ofNullable(orderItemMap.get(itemCommand.getOrderItemId()))
                    .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单明细不存在"));
            validateAdminReturnItem(orderItem, itemCommand, existingRefunds.get(orderItem.getId()));
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
                                         RefundAggregate existingRefund) {
        BigDecimal requestedQuantity = defaultAmount(itemCommand.getQuantity());
        BigDecimal requestedRefundAmount = defaultAmount(itemCommand.getRefundAmount());
        BigDecimal existingQuantity = existingRefund != null ? existingRefund.quantity : BigDecimal.ZERO;
        BigDecimal existingRefundAmount = existingRefund != null ? existingRefund.refundAmount : BigDecimal.ZERO;

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
            inCommand.setQuantity(resolveRestockQuantity(item, orderItem, product));
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
