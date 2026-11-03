package com.agri_system.application.order.service;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import com.agri_system.application.order.service.OrderApplicationService.ReassignmentPreview;

@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {
    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final StaffRepository staffRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.agri_system.domain.system.log.repository.SysOperationLogRepository operationLogRepository;

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

    private Integer resolveDeliveryType(OrderDelivery delivery) { return delivery==null?null:delivery.getDeliveryType(); }
}
