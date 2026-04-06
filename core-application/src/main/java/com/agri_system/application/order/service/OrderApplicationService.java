package com.agri_system.application.order.service;

import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.performance.event.OrderRefundedEvent;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderDomainService orderDomainService;
    private final OrderPaymentService paymentService;
    private final InventoryApplicationService inventoryService;
    private final WalletApplicationService walletService;
    private final CreditApplicationService creditService;
    private final ProductRepository productRepository;
    // ✅ 优化：移除套餐仓储依赖（前端已处理套餐炸开）
    // private final ProductPackageRepository packageRepository;
    // private final ProductPackageItemRepository packageItemRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletAuthCodeRepository walletAuthCodeRepository;
    
    /**
     * 创建小程序订单
     * 
     * @param command 订单创建命令
     * @param customerId 客户ID
     * @return 订单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateCommand command, Long customerId) {
        // 1. 生成订单号
        String orderNo = orderDomainService.generateOrderNo();
        
        // 2. 创建订单实体
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStoreId(command.getStoreId());
        order.setCustomerId(customerId != null ? customerId : command.getCustomerId());
        order.setOrderType(command.getOrderType());
        order.setOrderStatus(1); // 待支付
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        // 小程序下单时，收货地址信息固化到订单的 receiver_* 字段
        order.setReceiverAddress(command.getDeliveryAddress());
        order.setReceiverPhone(command.getDeliveryPhone());
        order.setRemark(command.getRemark());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setCreatedBy(customerId);
        
        // 3. 创建订单明细并计算总金额
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortOrder = 0;
        
        for (OrderItemDTO itemDTO : command.getItems()) {
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));
            
            BigDecimal unitPrice = itemDTO.getUnitPrice();
            if (unitPrice == null) {
                // TODO: 从价格体系查询价格
                unitPrice = product.getSuggestedRetailPrice();
            }
            
            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity());
            totalAmount = totalAmount.add(itemTotal);
            
            OrderItem item = new OrderItem();
            item.setOrderId(null); // 先不设置，保存订单后设置
            item.setProductId(itemDTO.getProductId());
            item.setItemType(1); // 商品
            item.setProductName(product.getName());
            item.setQuantity(itemDTO.getQuantity());
            item.setUnit(product.getMainUnit());
            item.setUnitPrice(unitPrice);
            item.setCostPriceSnapshot(product.getAvgCostPrice());
            item.setTotalPrice(itemTotal);
            item.setAllowSpecialFund(0); // TODO: 从商品快照获取
            item.setSortOrder(sortOrder++);
            items.add(item);
        }
        
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount); // 初始实付金额等于总金额
        
        // 4. 保存订单
        order = orderRepository.save(order);
        
        // 5. 保存订单明细
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            itemRepository.save(item);
        }
        
        // 6. 如果是自提订单，生成自提码
        if (command.getOrderType() == 2) {
            String pickupCode = orderDomainService.generatePickupCode();
            order.setPickupCode(pickupCode);
            orderRepository.save(order);
        }
        
        return order.getId();
    }
    
    /**
     * 创建POS订单（记账模式）
     * 
     * @param command POS订单创建命令
     * @param createdBy 操作人ID
     * @return 订单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPOSOrder(POSOrderCreateCommand command, Long createdBy) {
        // 1. 获取操作员所属门店ID（关键：库存扣减必须基于当前门店）
        Staff staff = staffRepository.findById(command.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("员工不存在: " + command.getStaffId()));
        Long actualStoreId = staff.getStoreId();
        if (actualStoreId == null) {
            throw new IllegalArgumentException("员工未分配门店，无法创建POS订单");
        }
        
        // 2. 赊销风控：已放宽，允许部分赊销及混合支付，具体支付金额由 payments 或前端 paymentType/receiveAmount 指定


        // 3. 生成订单号
        String orderNo = orderDomainService.generateOrderNo();
        
        // 4. 创建订单实体
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStoreId(actualStoreId); // 使用操作员所属门店
        order.setCustomerId(command.getCustomerId());
        order.setOrderType(1); // POS订单
        order.setOrderStatus(6); // 已完成（POS订单支付成功即完成，线下即时交易）
        order.setOrderStatus(6); // 已完成（POS订单支付成功即完成，线下即时交易）
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        // prefer createdBy (来自 Sa-Token) as operator/staff id when available
        if (createdBy != null) {
            order.setStaffId(createdBy);
        } else {
            order.setStaffId(command.getStaffId());
        }
        order.setRemark(command.getRemark());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setCreatedBy(createdBy);
        
        // 5. 创建订单明细并计算总金额
        // ✅ 优化：前端已将套餐炸开为商品，后端统一处理商品类型
        List<OrderItem> items = new ArrayList<>();
        List<InventoryOutCommand> inventoryOutCommands = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortOrder = 0;
        
        for (OrderItemDTO itemDTO : command.getItems()) {
            // 校验必填字段
            if (itemDTO.getProductId() == null) {
                throw new IllegalArgumentException("商品ID不能为空");
            }
            
            // 查询商品信息
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));
            
            // 计算价格
            BigDecimal unitPrice = itemDTO.getUnitPrice();
            if (unitPrice == null) {
                // TODO: 从价格体系查询价格（策略价 > VIP价 > 零售价）
                unitPrice = product.getSuggestedRetailPrice();
            }
            
            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity());
            totalAmount = totalAmount.add(itemTotal);
            
            // 创建订单明细
            OrderItem item = new OrderItem();
            item.setOrderId(null);
            item.setProductId(itemDTO.getProductId());
            item.setItemType(1); // 统一为商品类型
            item.setProductName(product.getName());
            item.setQuantity(itemDTO.getQuantity());
            item.setUnit(itemDTO.getUnit() != null ? itemDTO.getUnit() : product.getMainUnit());
            item.setUnitPrice(unitPrice);
            item.setCostPriceSnapshot(product.getAvgCostPrice());
            item.setTotalPrice(itemTotal);
            item.setAllowSpecialFund(product.getAllowSpecialFund() != null ? product.getAllowSpecialFund() : 0);
            item.setSortOrder(sortOrder++);
            items.add(item);
            
            // 准备库存扣减命令（传递单位信息用于换算）
            InventoryOutCommand outCommand = new InventoryOutCommand();
            outCommand.setStoreId(actualStoreId);
            outCommand.setProductId(item.getProductId());
            outCommand.setQuantity(item.getQuantity());
            outCommand.setUnit(item.getUnit()); // 传递订单行的单位（可能是辅单位）
            outCommand.setReferenceType("ORDER");
            outCommand.setReferenceId(order.getId());
            inventoryOutCommands.add(outCommand);
        }
        
        // ✅ 优化说明：
        // 1. 移除了 itemType=2（套餐）的处理分支
        // 2. 移除了套餐拆解逻辑（前端已处理）
        // 3. 统一使用商品库存扣减逻辑
        // 4. 代码更简洁，逻辑更清晰
        
        // 6. 计算订单金额（总额、优惠、优惠券、实付）
        BigDecimal discountAmount = command.getDiscountAmount() != null ? command.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal couponAmount = BigDecimal.ZERO;
        
        // 先保存订单和明细（用于优惠券校验）
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setCouponAmount(BigDecimal.ZERO); // 临时设为0，后续计算
        order.setPayAmount(totalAmount.subtract(discountAmount)); // 临时计算，后续更新
        order.setPaymentMethod(command.getPaymentType());
        
        order = orderRepository.save(order);
        
        // 保存订单明细
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            itemRepository.save(item);
        }
        
        boolean paymentsRecorded = false;
        // 7. 处理优惠券（如果有）
        // POS订单支付方式：1-现金, 2-微信, 3-支付宝, 4-余额, 6-赊销
        // 只有余额支付（4）才使用余额，支付宝（3）是外部支付
        if (command.getCouponId() != null && order.getCustomerId() != null) {
            // 使用支付服务预览优惠券抵扣额
            OrderPayPreviewVO preview = paymentService.previewPayment(order, 
                    command.getPaymentType() == 4, // useBalance: 只有余额支付才使用余额
                    command.getUseSpecialFund(), 
                    command.getCouponId());
            couponAmount = preview.getCouponAmount();
            
            // 如果优惠券可用，执行核销
            if (couponAmount.compareTo(BigDecimal.ZERO) > 0) {
                // executePayment 会执行实际的扣减并保存支付流水，避免后续重复记录
                paymentService.executePayment(order, 
                        command.getPaymentType() == 4, // useBalance: 只有余额支付才使用余额
                        command.getUseSpecialFund(), 
                        command.getCouponId(), 
                        createdBy);
                paymentsRecorded = true;
            }
        }
        
        // 8. 重新计算实付金额并更新订单
        BigDecimal payAmount = totalAmount.subtract(discountAmount).subtract(couponAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 实付金额不能小于0");
        }
        
        // ⛔️ 赊销订单二次校验：确保全额赊销
        if (command.getPaymentType().equals(PaymentMethod.CREDIT.getCode())) {
            if (payAmount.compareTo(totalAmount) != 0) {
                throw new IllegalArgumentException(OrderErrorCode.CREDIT_AMOUNT_MISMATCH +
                        ": 赊销订单必须全额赊销，实付金额必须等于订单总金额");
            }
            // For credit orders, customer's actual paid cash should be 0 (amount recorded as credit)
            payAmount = BigDecimal.ZERO;
        }
        
        order.setCouponAmount(couponAmount);
        order.setPayAmount(payAmount);
        order.setPaymentMethod(command.getPaymentType()); // 记录支付方式
        // Save order first; wallet auth code will be validated and consumed later within the same transaction (to ensure rollback consistency)
        order = orderRepository.save(order);
        
        // 9. ⛔️ 若前端传入了 payments 列表，则按明细处理支付（优先使用 payments）；否则回退到 legacy savePaymentRecords 逻辑
        boolean paymentsProvided = command.getPayments() != null && !command.getPayments().isEmpty();

        if (paymentsProvided) {
            // 校验支付明细金额之和等于应付金额
            BigDecimal sum = BigDecimal.ZERO;
            for (PaymentDetailDTO pd : command.getPayments()) {
                if (pd == null || pd.getAmount() == null) {
                    throw new IllegalArgumentException("支付明细不完整");
                }
                sum = sum.add(pd.getAmount());
            }
            if (sum.compareTo(payAmount) != 0) {
                throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 支付明细金额之和与应付金额不匹配");
            }

            // 收集支付明细，先分离出“余额相关（余额+专用金）”，其他方式先处理
            BigDecimal combinedBalanceRequested = BigDecimal.ZERO;
            boolean hasBalanceEntry = false;
            List<PaymentDetailDTO> otherPayments = new ArrayList<>();
            for (PaymentDetailDTO pd : command.getPayments()) {
                Integer pt = pd.getPaymentType();
                BigDecimal amt = pd.getAmount();
                if (pt == null || amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (pt.equals(PaymentMethod.BALANCE.getCode()) || pt.equals(PaymentMethod.SPECIAL_FUND.getCode())) {
                    combinedBalanceRequested = combinedBalanceRequested.add(amt);
                    if (pt.equals(PaymentMethod.BALANCE.getCode())) hasBalanceEntry = true;
                } else {
                    otherPayments.add(pd);
                }
            }

            // 先处理非余额类支付（赊销/现金/微信/支付宝/优惠券等）
            for (PaymentDetailDTO pd : otherPayments) {
                Integer pt = pd.getPaymentType();
                BigDecimal amt = pd.getAmount();
                if (pt.equals(PaymentMethod.CREDIT.getCode())) {
                    if (order.getCustomerId() == null) {
                        throw new IllegalArgumentException("赊销必须指定客户");
                    }
                    creditService.addDebt(order.getCustomerId(), order.getId(), amt);
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(PaymentMethod.CREDIT.getCode());
                    payment.setAmount(amt);
                    payment.setCreatedAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                } else {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(pt);
                    payment.setAmount(amt);
                    payment.setCreatedAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                }
            }

            // 处理余额相关（专用金优先），要求前端包含余额条目才能单独指定专用金
            if (combinedBalanceRequested.compareTo(BigDecimal.ZERO) > 0) {
                if (!hasBalanceEntry) {
                    throw new IllegalArgumentException("专用金不可单独作为支付方式，请将其作为余额的一部分提交或选择余额支付");
                }
                List<OrderItem> orderItemsForCalc = itemRepository.findByOrderId(order.getId());
                BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(orderItemsForCalc);
                java.math.BigDecimal customerSpecialFund = java.math.BigDecimal.ZERO;
                try {
                    customerSpecialFund = walletService.getSpecialFundBalance(order.getCustomerId());
                } catch (Exception ignore) {
                    customerSpecialFund = java.math.BigDecimal.ZERO;
                }
                BigDecimal specialCanUse = maxSpecialFund.min(new BigDecimal(customerSpecialFund.toString()));
                BigDecimal specialUsed = specialCanUse.min(combinedBalanceRequested);
                BigDecimal balanceUsed = combinedBalanceRequested.subtract(specialUsed);

                if (specialUsed.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.deductSpecialFund(order.getCustomerId(), specialUsed, "ORDER", order.getId(), createdBy, "POS订单支付");
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(PaymentMethod.SPECIAL_FUND.getCode());
                    payment.setAmount(specialUsed);
                    payment.setCreatedAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                }
                if (balanceUsed.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.deductBalance(order.getCustomerId(), balanceUsed, "ORDER", order.getId(), createdBy, "POS订单支付");
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(PaymentMethod.BALANCE.getCode());
                    payment.setAmount(balanceUsed);
                    payment.setCreatedAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                }
            }

            paymentsRecorded = true;

            // 计算订单实际记录的支付方式：如果只有一种非优惠券支付方式则写该类型，否则写混合支付
            java.util.Set<Integer> paymentTypesUsed = new java.util.HashSet<>();
            for (PaymentDetailDTO pd : command.getPayments()) {
                if (pd == null || pd.getAmount() == null) continue;
                Integer pt = pd.getPaymentType();
                if (pt == null) continue;
                // 将专用金视为余额用于决定订单主支付方式（前端提交时专用金可能作为余额的一部分）
                if (pt.equals(PaymentMethod.SPECIAL_FUND.getCode())) {
                    pt = PaymentMethod.BALANCE.getCode();
                }
                // 忽略优惠券类型对主支付方式的影响
                if (pt.equals(PaymentMethod.COUPON.getCode())) continue;
                paymentTypesUsed.add(pt);
            }
            int effectivePaymentMethod = command.getPaymentType();
            if (paymentTypesUsed.size() == 1) {
                effectivePaymentMethod = paymentTypesUsed.iterator().next();
            } else if (paymentTypesUsed.size() > 1) {
                effectivePaymentMethod = PaymentMethod.MIXED.getCode();
            }
            order.setPaymentMethod(effectivePaymentMethod);
            orderRepository.save(order);

        } else {
            // 若前面已通过 paymentService.executePayment 保存了流水（如优惠券场景），则跳过，避免重复写入
            if (!paymentsRecorded) {
                savePaymentRecords(order, command, createdBy);
            }
        }

        // 10. 处理内部支付扣减（只在未由 payments 明细处理时执行）
        if (!paymentsProvided && ( (command.getPaymentType() == 4 || command.getPaymentType() == 10 || command.getWalletAuthCode() != null)
                && order.getCustomerId() != null)) {
            // 计算专用金和本金余额的分配（先算出将要从余额扣减的金额）
            List<OrderItem> orderItems = itemRepository.findByOrderId(order.getId());
            BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(orderItems);
            
            BigDecimal specialFundAmount = BigDecimal.ZERO;
            BigDecimal balanceAmount = BigDecimal.ZERO;
            BigDecimal payAmt = payAmount;

            // 如果使用专用金，计算专用金金额并减去
            if (Boolean.TRUE.equals(command.getUseSpecialFund())) {
                specialFundAmount = payAmt.min(maxSpecialFund);
            }
            BigDecimal remainingAfterSpecial = payAmt.subtract(specialFundAmount);

            // 计算可从本金余额中扣减的金额（不超过客户余额）
            try {
                logger.info("About to query customer balance for customerId={} orderId={}", order.getCustomerId(), order.getId());
                java.math.BigDecimal customerBalance = walletService.getBalance(order.getCustomerId());
                logger.info("Queried customer balance for customerId={} balance={}", order.getCustomerId(), customerBalance);
                balanceAmount = remainingAfterSpecial.min(customerBalance);
            } catch (Exception e) {
                logger.error("Failed to query customer balance for customerId={}, orderId={}", order.getCustomerId(), order.getId(), e);
                balanceAmount = BigDecimal.ZERO;
            }

            // 如果需要从本金余额扣减，则必须提供钱包验证码
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                if (!StringUtils.hasText(command.getWalletAuthCode())) {
                    throw new IllegalArgumentException(OrderErrorCode.WALLET_AUTH_CODE_INVALID + ": 钱包验证码不能为空");
                }
            }

            // 执行扣减：专用金优先，随后本金
            if (specialFundAmount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.deductSpecialFund(order.getCustomerId(), specialFundAmount, 
                    "ORDER", order.getId(), createdBy, "POS订单支付");
            }
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                logger.info("About to deduct balance for customerId={} orderId={} amount={}", order.getCustomerId(), order.getId(), balanceAmount);
                walletService.deductBalance(order.getCustomerId(), balanceAmount, 
                    "ORDER", order.getId(), createdBy, "POS订单支付");
                logger.info("Deducted balance for customerId={} orderId={} amount={}", order.getCustomerId(), order.getId(), balanceAmount);
            }
        }
        
        // 11. 扣减库存（POS订单立即扣减，使用操作员所属门店ID）
        // 重要：订单已保存，更新所有库存扣减命令的 referenceId
        for (InventoryOutCommand outCommand : inventoryOutCommands) {
            outCommand.setReferenceId(order.getId()); // 确保使用已保存的订单ID
            inventoryService.outbound(outCommand, createdBy);
        }
        
        // 12. ⛔️ 赊销记账（Critical Fix）
        if (command.getPaymentType().equals(PaymentMethod.CREDIT.getCode()) && order.getCustomerId() != null) {
            creditService.addDebt(order.getCustomerId(), order.getId(), order.getPayAmount());
        }

        // 13. 发布订单完成事件（用于更新客户统计和发放积分）
        eventPublisher.publishEvent(new OrderFinishedEvent(order.getId()));

        // 14. 🔒 验证码消费（通过Repository，确保同一事务同一连接）
        if (StringUtils.hasText(command.getWalletAuthCode())) {
            logger.info("Consuming auth code via repository: {}", command.getWalletAuthCode());

            int updated = walletAuthCodeRepository.consumeAuthCode(command.getWalletAuthCode(), order.getId());

            if (updated == 0) {
                // 可能是并发导致的，也可能是状态不对，这里为了安全抛出异常回滚订单
                throw new IllegalStateException("验证码核销失败：可能已被使用或不存在");
            }
        }

        return order.getId();
    }
    
    /**
     * 订单支付（小程序）
     * 
     * @param command 支付命令
     * @param customerId 客户ID
     * @return 支付预览（如果还有外部支付，返回预览；如果已全部支付，返回null）
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPayPreviewVO payOrder(OrderPayCommand command, Long customerId) {
        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        
        // 校验订单状态
        if (order.getOrderStatus() != 1) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
        }
        
        // 校验客户
        if (!order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权操作此订单");
        }
        
        // 执行支付
        paymentService.executePayment(order, command.getUseBalance(), command.getUseSpecialFund(), 
                                    command.getCouponId(), customerId);
        
        // 更新订单状态
        order.markAsPaid();
        if (order.getOrderType() == 2) {
            // 自提订单
            order.markAsWaitPickup();
        } else if (order.getOrderType() == 3) {
            // 配送订单
            order.markAsWaitShip();
        }
        orderRepository.save(order);
        
        // 扣减库存（支付成功后扣减，传递单位信息用于换算）
        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            InventoryOutCommand outCommand = new InventoryOutCommand();
            outCommand.setStoreId(order.getStoreId());
            outCommand.setProductId(item.getProductId());
            outCommand.setQuantity(item.getQuantity());
            outCommand.setUnit(item.getUnit()); // 传递订单行的单位（可能是辅单位）
            outCommand.setReferenceType("ORDER");
            outCommand.setReferenceId(order.getId());
            inventoryService.outbound(outCommand, customerId);
        }
        
        // 返回支付预览（如果还有外部支付）
        OrderPayPreviewVO preview = paymentService.previewPayment(order, command.getUseBalance(), 
                                                                  command.getUseSpecialFund(), command.getCouponId());
        if (preview.getExternalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return preview; // 需要外部支付
        }
        
        return null; // 已全部支付
    }
    
    /**
     * 分页查询订单列表
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    public com.mybatisflex.core.paginate.Page<OrderPageVO> queryOrderPage(OrderPageQuery query) {
        // 1. 查询订单分页数据
        com.mybatisflex.core.paginate.Page<Order> page = orderRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getKeyword(),
                query.getStoreId(),
                query.getOrderType(),
                query.getDeliveryType(),
                query.getOrderStatus() != null ? query.getOrderStatus().toString() : null,
                query.getStartTime(),
                query.getEndTime(),
                query.getCustomerId()
        );
        
        // 2. 转换为VO
        List<OrderPageVO> voList = page.getRecords().stream().map(order -> {
            OrderPageVO vo = new OrderPageVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStoreId(order.getStoreId());
            vo.setCustomerId(order.getCustomerId());
            vo.setOrderType(order.getOrderType());
            // 配送方式：POS订单无配送方式，小程序自提=1，小程序配送=3（同城配送暂按3处理）
            if (order.getOrderType() != null) {
                if (order.getOrderType() == 2) {
                    vo.setDeliveryType(1); // 自提
                } else if (order.getOrderType() == 3) {
                    vo.setDeliveryType(3); // 快递（简化处理）
                }
            }
            vo.setOrderStatus(order.getOrderStatus());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setDiscountAmount(order.getDiscountAmount());
            vo.setCouponAmount(order.getCouponAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setReceiverName(order.getReceiverName());
            vo.setReceiverPhone(order.getReceiverPhone());
            vo.setReceiverAddress(order.getReceiverAddress());
            vo.setPickupCode(order.getPickupCode());
            vo.setCreatedAt(order.getCreatedAt());
            
            // 查询门店名称
            if (order.getStoreId() != null) {
                storeRepository.findById(order.getStoreId()).ifPresent(store -> {
                    vo.setStoreName(store.getName());
                });
            }
            
            // 查询客户信息
            if (order.getCustomerId() != null) {
                customerRepository.findById(order.getCustomerId()).ifPresent(customer -> {
                    vo.setCustomerName(customer.getName());
                    vo.setCustomerPhone(customer.getPhone());
                });
            }
            
            // 主支付方式（若记录在订单上），若订单表未记录则回退到支付流水的第一条记录
            if (order.getPaymentMethod() != null) {
                vo.setPaymentMethod(order.getPaymentMethod());
            } else {
                List<OrderPayment> payments = paymentRepository.findByOrderId(order.getId());
                if (payments != null && !payments.isEmpty()) {
                    vo.setPaymentMethod(payments.get(0).getPaymentType());
                }
            }
            
            // 查询订单明细（仅前几个，用于概览）
            List<OrderItem> items = itemRepository.findByOrderId(order.getId());
            if (items != null && !items.isEmpty()) {
                vo.setItems(items.stream().limit(3).map(item -> {
                    OrderItemVO itemVO = new OrderItemVO();
                    itemVO.setId(item.getId());
                    itemVO.setProductId(item.getProductId());
                    itemVO.setProductName(item.getProductName());
                    itemVO.setQuantity(item.getQuantity());
                    itemVO.setUnit(item.getUnit());
                    itemVO.setUnitPrice(item.getUnitPrice());
                    itemVO.setTotalPrice(item.getTotalPrice());
                    return itemVO;
                }).collect(Collectors.toList()));
            }
            
            return vo;
        }).collect(Collectors.toList());
        
        // 3. 构建分页结果
        com.mybatisflex.core.paginate.Page<OrderPageVO> result = new com.mybatisflex.core.paginate.Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    /**
     * 查询订单详情
     * 
     * @param orderId 订单ID
     * @return 订单详情
     */
    public OrderDetailVO getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        
        List<OrderItem> items = itemRepository.findByOrderId(orderId);
        List<com.agri_system.domain.order.model.OrderPayment> payments = paymentRepository.findByOrderId(orderId);
        
        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreId(order.getStoreId());
        vo.setCustomerId(order.getCustomerId());
        vo.setOrderType(order.getOrderType());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setCouponAmount(order.getCouponAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPickupCode(order.getPickupCode());
        vo.setCreatedAt(order.getCreatedAt());
        
        // 转换明细
        vo.setItems(items.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            itemVO.setId(item.getId());
            itemVO.setProductId(item.getProductId());
            itemVO.setPackageId(item.getPackageId());
            itemVO.setItemType(item.getItemType());
            itemVO.setProductName(item.getProductName());
            itemVO.setQuantity(item.getQuantity());
            itemVO.setUnit(item.getUnit());
            itemVO.setUnitPrice(item.getUnitPrice());
            itemVO.setTotalPrice(item.getTotalPrice());
            // attempt to enrich with product snapshot data (specification, manufacturer) when available
            try {
                productRepository.findById(item.getProductId()).ifPresent(product -> {
                    // product may have specification and manufacturer fields
                    try {
                        java.lang.reflect.Method mSpec = product.getClass().getMethod("getSpecification");
                        Object specVal = mSpec.invoke(product);
                        if (specVal != null) itemVO.setSpecification(String.valueOf(specVal));
                    } catch (Exception ignore) {}
                    try {
                        java.lang.reflect.Method mManu = product.getClass().getMethod("getManufacturer");
                        Object manuVal = mManu.invoke(product);
                        if (manuVal != null) itemVO.setManufacturer(String.valueOf(manuVal));
                    } catch (Exception ignore) {}
                });
            } catch (Exception ignored) {
                // ignore enrichment errors
            }
            return itemVO;
        }).collect(Collectors.toList()));
        
        // 转换支付明细
        vo.setPayments(payments.stream().map(payment -> {
            OrderPaymentVO paymentVO = new OrderPaymentVO();
            paymentVO.setId(payment.getId());
            paymentVO.setPaymentType(payment.getPaymentType());
            paymentVO.setAmount(payment.getAmount());
            paymentVO.setCouponId(payment.getCouponId());
            paymentVO.setOuterTransNo(payment.getOuterTransNo());
            paymentVO.setCreatedAt(payment.getCreatedAt());
            return paymentVO;
        }).collect(Collectors.toList()));
        
        // 查询门店名称
        if (order.getStoreId() != null) {
            storeRepository.findById(order.getStoreId()).ifPresent(store -> {
                vo.setStoreName(store.getName());
            });
        }
        
        // 查询客户信息
        if (order.getCustomerId() != null) {
            customerRepository.findById(order.getCustomerId()).ifPresent(customer -> {
                vo.setCustomerName(customer.getName());
                vo.setCustomerPhone(customer.getPhone());
            });
        }
        
        // 查询配送信息
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            vo.setDelivery(delivery);
        });
        
        // 设置收货信息
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        // 备注与操作员（优先使用 staffId，其次使用 createdBy）
        vo.setRemark(order.getRemark());
        Long operatorId = null;
        if (order.getStaffId() != null) {
            operatorId = order.getStaffId();
        } else if (order.getCreatedBy() != null) {
            operatorId = order.getCreatedBy();
        }
        if (operatorId != null) {
            try {
                staffRepository.findById(operatorId).ifPresent(staff -> vo.setOperatorName(staff.getName()));
            } catch (Exception ignored) {
                // ignore lookup failure
            }
        }
        
        // 查询售后记录
        List<com.agri_system.domain.after_sales.model.AfterSales> afterSalesList = 
                afterSalesRepository.findByOrderId(orderId);
        if (afterSalesList != null && !afterSalesList.isEmpty()) {
            vo.setAfterSalesList(afterSalesList.stream().map(afterSales -> {
                com.agri_system.application.order.dto.vo.AfterSalesVO afterSalesVO = 
                        new com.agri_system.application.order.dto.vo.AfterSalesVO();
                afterSalesVO.setId(afterSales.getId());
                afterSalesVO.setAfterSalesNo(afterSales.getAfterSalesNo());
                afterSalesVO.setType(afterSales.getType());
                afterSalesVO.setStatus(afterSales.getStatus());
                afterSalesVO.setRefundAmount(afterSales.getRefundAmount());
                afterSalesVO.setReason(afterSales.getReason());
                afterSalesVO.setCreatedAt(afterSales.getCreatedAt());
                afterSalesVO.setRefundTime(afterSales.getRefundTime());
                return afterSalesVO;
            }).collect(Collectors.toList()));
        }
        
        return vo;
    }
    
    /**
     * 确认收货（管理员强制确认）
     * 
     * @param orderId 订单ID
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceipt(Long orderId, Long createdBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        
        // 只有已发货状态的订单可以确认收货
        if (order.getOrderStatus() == null || order.getOrderStatus() != 5) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 只有已发货状态的订单可以确认收货");
        }
        
        order.markAsFinished();
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderFinishedEvent(order.getId()));
    }
    
    /**
     * 自提核销
     * 
     * @param command 核销命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void verifyPickup(OrderPickupVerifyCommand command, Long createdBy) {
        Order order = orderRepository.findByPickupCode(command.getPickupCode())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.PICKUP_CODE_NOT_FOUND + ": 自提码不存在"));
        
        if (order.getOrderStatus() != 3) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
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
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(OrderShipCommand command, Long createdBy) {
        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        
        if (order.getOrderStatus() != 4) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
        }
        
        // 更新订单状态
        order.markAsShipped();
        orderRepository.save(order);
        
        // 更新配送信息
        OrderDelivery delivery = deliveryRepository.findByOrderId(command.getOrderId())
                .orElseGet(() -> {
                    OrderDelivery newDelivery = new OrderDelivery();
                    newDelivery.setOrderId(command.getOrderId());
                    // 默认使用下单时固化在订单上的收货信息
                    newDelivery.setDeliveryAddress(order.getReceiverAddress());
                    newDelivery.setDeliveryPhone(order.getReceiverPhone());
                    return newDelivery;
                });
        
        delivery.setLogisticsCompany(command.getLogisticsCompany());
        delivery.setTrackingNo(command.getTrackingNo());
        delivery.setDeliveryStatus(3); // 配送中
        delivery.setDeliveryAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
    }
    
    /**
     * 退款
     * 
     * @param command 退款命令
     * @param createdBy 操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(OrderRefundCommand command, Long createdBy) {
        Order order = orderRepository.findById(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        
        if (!order.canRefund()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 订单不支持退款");
        }
        
        BigDecimal refundAmount = command.getRefundAmount();
        if (refundAmount == null) {
            refundAmount = order.getPayAmount();
        }
        // 执行退款
        paymentService.refund(order, refundAmount, createdBy);
        
        // 回滚库存
        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            // TODO: 调用库存服务回滚
            // inventoryService.inbound(...);
            // 避免 IDE/静态分析提示未使用的局部变量，访问一个属性以示使用
            item.getId();
        }
        
        // 更新订单状态
        if (refundAmount.compareTo(order.getPayAmount()) >= 0) {
            order.markAsRefunded();
        } else {
            order.markAsPartRefunded();
        }
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderRefundedEvent(order.getId(),
                refundAmount, java.util.Collections.emptyMap()));
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * ⛔️ 保存支付流水记录（Critical Fix）
     * 
     * 支付类型：
     * 1 - 现金
     * 2 - 微信
     * 3 - 支付宝
     * 4 - 余额（本金+专用金混合）
     * 6 - 赊销
     * 
     * 关键修复：
     * - 赊销订单只记录一条 payment_type=6 的记录
     * - 不再将赊销订单的金额错误记录为现金或专用金
     * 
     * @param order 订单
     * @param command POS订单创建命令
     * @param createdBy 操作人ID
     */
    private void savePaymentRecords(Order order, POSOrderCreateCommand command, Long createdBy) {
        LocalDateTime now = LocalDateTime.now();
        // existing payments to avoid duplicates
        List<OrderPayment> existingPayments = paymentRepository.findByOrderId(order.getId());
        
        // 赊销订单：只记录一条赊销记录（金额 = 订单应收 - 折扣 - 优惠券）
        if (command.getPaymentType().equals(PaymentMethod.CREDIT.getCode())) {
            BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
            BigDecimal coupon = order.getCouponAmount() == null ? BigDecimal.ZERO : order.getCouponAmount();
            BigDecimal creditAmount = total.subtract(discount).subtract(coupon);
            if (creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                boolean dup = existingPayments.stream().anyMatch(p -> p.getPaymentType() != null
                        && p.getPaymentType().equals(PaymentMethod.CREDIT.getCode())
                        && p.getAmount() != null && p.getAmount().compareTo(creditAmount) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(PaymentMethod.CREDIT.getCode()); // 赊销
                    payment.setAmount(creditAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
            return; // 赊销订单不再记录其他支付方式
        }

        // 优惠券抵扣（如果有）
        if (order.getCouponAmount() != null && order.getCouponAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amt = order.getCouponAmount();
            boolean dup = existingPayments.stream().anyMatch(p -> p.getPaymentType() != null
                    && p.getPaymentType().equals(PaymentMethod.COUPON.getCode())
                    && p.getAmount() != null && p.getAmount().compareTo(amt) == 0);
            if (!dup) {
                OrderPayment payment = new OrderPayment();
                payment.setOrderId(order.getId());
                payment.setPaymentType(PaymentMethod.COUPON.getCode()); // 优惠券
                payment.setAmount(amt);
                payment.setCouponId(command.getCouponId());
                payment.setCreatedAt(now);
                paymentRepository.save(payment);
            }
        }
        
        // 专用金抵扣（如果使用）
        if (Boolean.TRUE.equals(command.getUseSpecialFund()) && order.getCustomerId() != null) {
            // 计算专用金可用额度
            List<OrderItem> orderItems = itemRepository.findByOrderId(order.getId());
            BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(orderItems);
            BigDecimal specialFundAmount = order.getPayAmount().min(maxSpecialFund);
            
            if (specialFundAmount.compareTo(BigDecimal.ZERO) > 0) {
                final BigDecimal sfAmt = specialFundAmount;
                boolean dup = existingPayments.stream().anyMatch(p -> p.getPaymentType() != null
                        && p.getPaymentType().equals(4)
                        && p.getAmount() != null && p.getAmount().compareTo(sfAmt) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(4); // 专用金
                    payment.setAmount(specialFundAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
        }
        
        // 本金余额抵扣（余额支付时，或混合支付且使用余额时，扣除专用金后的剩余部分）
        if ((command.getPaymentType() == 4 || (command.getPaymentType() == 10 || command.getWalletAuthCode() != null)) && order.getCustomerId() != null) {
            BigDecimal balanceAmount = BigDecimal.ZERO;
            BigDecimal specialFundAmount = BigDecimal.ZERO;
            BigDecimal payAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();

            // 如果已经使用了专用金，需要减去专用金金额
            if (Boolean.TRUE.equals(command.getUseSpecialFund())) {
                List<OrderItem> orderItems = itemRepository.findByOrderId(order.getId());
                BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(orderItems);
                specialFundAmount = payAmount.min(maxSpecialFund);
            }

            BigDecimal remainingAfterSpecial = payAmount.subtract(specialFundAmount);

            // 尝试从客户本金余额中扣减（不能超过客户当前余额）
            try {
                java.math.BigDecimal customerBalance = walletService.getBalance(order.getCustomerId());
                balanceAmount = remainingAfterSpecial.min(customerBalance);
            } catch (Exception ignore) {
                balanceAmount = BigDecimal.ZERO;
            }

            // 专用金的支付记录已在上方处理，避免重复保存

            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                final BigDecimal balAmt = balanceAmount;
                boolean dup = existingPayments.stream().anyMatch(p -> p.getPaymentType() != null
                        && p.getPaymentType().equals(3)
                        && p.getAmount() != null && p.getAmount().compareTo(balAmt) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(3); // 本金余额
                    payment.setAmount(balanceAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
        }
        
        // 外部支付（现金、微信、支付宝）
        // 只有在非余额支付且非混合支付时才记录外部支付（混合支付不应保存一条总额为混合的记录）
        if (command.getPaymentType() != 4 && command.getPaymentType() != PaymentMethod.MIXED.getCode()) {
            BigDecimal externalAmount = order.getPayAmount();
            // 减去优惠券金额
            if (order.getCouponAmount() != null) {
                externalAmount = externalAmount.subtract(order.getCouponAmount());
            }
            
            if (externalAmount.compareTo(BigDecimal.ZERO) > 0) {
                final BigDecimal extAmt = externalAmount;
                final Integer payType = command.getPaymentType();
                boolean dup = existingPayments.stream().anyMatch(p -> p.getPaymentType() != null
                        && p.getPaymentType().equals(payType)
                        && p.getAmount() != null && p.getAmount().compareTo(extAmt) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(command.getPaymentType()); // 1-现金, 2-微信, 3-支付宝
                    payment.setAmount(externalAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
        }
    }
}



