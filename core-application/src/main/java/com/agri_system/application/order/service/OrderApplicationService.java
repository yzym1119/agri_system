package com.agri_system.application.order.service;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.domain.order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * 订单公开入口：保留原调用契约与事务，按业务职责委托专用服务。
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderQueryService queryService;
    private final OnlineOrderCreationService onlineOrderCreationService;
    private final PosOrderCreationService posOrderCreationService;
    private final ClientOrderPaymentService clientOrderPaymentService;
    private final OrderFulfillmentService fulfillmentService;
    private final OrderRefundService refundService;

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
        return clientOrderPaymentService.payOrder(command,customerId);
    }

    /**
     * 订单支付预览（小程序）
     */
    public OrderPayPreviewVO previewOrderPayment(OrderPayCommand command, Long customerId) {
        return clientOrderPaymentService.previewOrderPayment(command,customerId);
    }

    /**
     * 生成外部支付发起信息（小程序）
     */
    public OrderExternalPaymentIntentVO createExternalPaymentIntent(OrderPayCommand command, Long customerId) {
        return clientOrderPaymentService.createExternalPaymentIntent(command,customerId);
    }

    /**
     * 确认外部支付并完成订单（小程序）
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmExternalPayment(OrderExternalPaymentConfirmCommand command, Long customerId) {
        clientOrderPaymentService.confirmExternalPayment(command,customerId);
    }

    /**
     * 外部支付回调确认
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmExternalPaymentCallback(OrderExternalPaymentCallbackCommand command) {
        clientOrderPaymentService.confirmExternalPaymentCallback(command);
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

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmReceipt(Long orderId, Long createdBy) {
        fulfillmentService.confirmReceipt(orderId,createdBy);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmReceipt(Long orderId, Long createdBy, String confirmationKey) {
        fulfillmentService.confirmReceipt(orderId,createdBy,confirmationKey);
    }

    /** 手机本人签收也进入原订单锁；不能复用店长的整店发货权限或锁前授权。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Result confirmAssignedReceipt(Long orderId,String confirmationKey,Long staffId) {
        return fulfillmentService.confirmAssignedReceipt(orderId,confirmationKey,staffId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.agri_system.application.order.dto.vo.AssignedDeliveryVO.Preview previewAssignedReceipt(Long orderId,Long staffId) {
        return fulfillmentService.previewAssignedReceipt(orderId,staffId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void confirmReceiptForCustomer(Long orderId, Long customerId) {
        fulfillmentService.confirmReceiptForCustomer(orderId,customerId);
    }
    
    /**
     * 自提核销
     * 
     * @param command 核销命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void verifyPickup(OrderPickupVerifyCommand command, Long createdBy) {
        fulfillmentService.verifyPickup(command,createdBy);
    }
    
    /**
     * 发货
     * 
     * @param command 发货命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void shipOrder(OrderShipCommand command, Long createdBy) {
        fulfillmentService.shipOrder(command,createdBy);
    }

    public record ReassignmentPreview(String storeId,String orderId,String currentStaffId,String version,boolean canReassign,String blockReason) {}

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ReassignmentPreview previewDeliveryReassignment(Long orderId,Long operatorId) {
        return fulfillmentService.previewDeliveryReassignment(orderId,operatorId);
    }

    /** 明确改派与原签收争用同一父订单；不改变原发货重放语义。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reassignDelivery(Long orderId,Long expectedVersion,Long targetStaffId,String reason,Long operatorId) {
        fulfillmentService.reassignDelivery(orderId,expectedVersion,targetStaffId,reason,operatorId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public com.agri_system.application.order.dto.vo.OrderRefundPreviewVO previewRefund(Long orderId,
            com.agri_system.application.order.dto.command.OrderRefundPreviewCommand command) {
        return refundService.previewRefund(orderId,command);
    }

    /** 批准前核对原成交和原资金，只计算不执行退款，不先回库。 */
    public void validateAfterSalesApproval(Order order, com.agri_system.domain.after_sales.model.AfterSales applied) {
        refundService.validateAfterSalesApproval(order,applied);
    }

    /** 所有正式退款都必须先确认当前资金、金额和实退数量。 */
    public void validateRefundQuote(Order order,com.agri_system.application.order.dto.command.OrderRefundPreviewCommand request,BigDecimal amount,String quoteId) {
        refundService.validateRefundQuote(order,request,amount,quoteId);
    }

    /**
     * 退款
     * 
     * @param command 退款命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void refundOrder(OrderRefundCommand command, Long createdBy) {
        refundService.refundOrder(command,createdBy);
    }

}
