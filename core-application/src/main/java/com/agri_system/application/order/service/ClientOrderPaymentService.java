package com.agri_system.application.order.service;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.domain.customer.repository.CustomerRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 客户端支付与首次付款完成编排；不实现平台验签，不新增真实支付开关。 */
@Service
@RequiredArgsConstructor
public class ClientOrderPaymentService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderPaymentRepository paymentRepository;
    private final OrderPaymentIntentRepository paymentIntentRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderPaymentService paymentService;
    private final InventoryApplicationService inventoryService;
    private final CustomerRepository customerRepository;
    @Value("${payment.client-confirm.enabled:false}")
    private boolean clientPaymentConfirmEnabled;

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

}
