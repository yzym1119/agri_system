package com.agri_system.application.after_sales.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.after_sales.dto.command.ClientApplyCommand;
import com.agri_system.application.after_sales.exception.AfterSalesErrorCode;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.service.OrderPaymentService;
import com.agri_system.domain.after_sales.model.AfterSales;
import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.service.AfterSalesDomainService;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final InventoryApplicationService inventoryApplicationService;
    private final OrderPaymentService orderPaymentService;

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
            
            // 更新订单状态：全额退款 -> 已退款；否则 -> 部分退款
            if (refundAmount.compareTo(order.getPayAmount()) == 0) {
                order.markAsRefunded();
            } else {
                order.markAsPartRefunded();
            }
            orderRepository.save(order);
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

        // 更新订单状态：全额退款 -> 已退款；否则 -> 部分退款
        if (refundAmount.compareTo(order.getPayAmount()) == 0) {
            order.markAsRefunded();
        } else if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.markAsPartRefunded();
        }
        orderRepository.save(order);
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
        return status != null && (status.equals(PaymentMethod.COUPON.getCode()) || status.equals(PaymentMethod.CREDIT.getCode()));
    }

    private boolean isCreditOrder(Order order) {
        if (order.getPaymentMethod() == null) {
            return false;
        }
        return order.getPaymentMethod().equals(PaymentMethod.COUPON.getCode()) || order.getPaymentMethod().equals(PaymentMethod.CREDIT.getCode());
    }

    private List<AfterSalesItem> buildAdminItems(AfterSales afterSales,
                                                 AdminReturnCommand command,
                                                 Map<Long, OrderItem> orderItemMap) {
        BigDecimal total = BigDecimal.ZERO;
        List<AfterSalesItem> items = new ArrayList<>();
        for (AdminReturnCommand.ReturnItem itemCommand : command.getItems()) {
            OrderItem orderItem = Optional.ofNullable(orderItemMap.get(itemCommand.getOrderItemId()))
                    .orElseThrow(() -> new IllegalArgumentException(AfterSalesErrorCode.ORDER_NOT_FOUND + ": 订单明细不存在"));
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
}

