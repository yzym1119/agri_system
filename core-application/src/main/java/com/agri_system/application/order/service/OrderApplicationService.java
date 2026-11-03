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

    /** 直退和售后共享既有退款事实；没有另一套可写退款来源。 */


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
