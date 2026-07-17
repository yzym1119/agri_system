package com.agri_system.application.order.service;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    // ✅ 优化：移除套餐仓储依赖（前端已处理套餐炸开）
    // private final ProductPackageRepository packageRepository;
    // private final ProductPackageItemRepository packageItemRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerRepository customerRepository;
    private final AfterSalesRepository afterSalesRepository;
    private final AfterSalesItemRepository afterSalesItemRepository;
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
        Integer deliveryType = validateAndResolveCreateDeliveryType(command);
        Store store = validateAndLoadCreateStore(command);
        Long finalCustomerId = customerId != null ? customerId : command.getCustomerId();
        Customer customer = loadCreateOrderCustomer(finalCustomerId);
        String receiverName = resolveCreateOrderReceiverName(command, customer);

        // 1. 生成订单号
        String orderNo = orderDomainService.generateOrderNo();
        
        // 2. 创建订单实体
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStoreId(store.getId());
        order.setCustomerId(finalCustomerId);
        order.setOrderType(command.getOrderType());
        order.setOrderStatus(1); // 待支付
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setReceiverName(receiverName);
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
            
            String itemUnit = StringUtils.hasText(itemDTO.getUnit()) ? itemDTO.getUnit() : product.getMainUnit();
            // 客户端下单不信任前端传入单价，统一由后端价格体系计算
            BigDecimal unitPrice = productPricingService.resolveOrderItemUnitPrice(
                    product,
                    order.getStoreId(),
                    order.getCustomerId(),
                    itemUnit
            );
            
            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity());
            totalAmount = totalAmount.add(itemTotal);
            
            OrderItem item = new OrderItem();
            item.setOrderId(null); // 先不设置，保存订单后设置
            item.setProductId(itemDTO.getProductId());
            item.setPackageId(itemDTO.getPackageId());
            item.setItemType(1); // 商品
            item.setProductName(product.getName());
            item.setQuantity(itemDTO.getQuantity());
            item.setUnit(itemUnit);
            item.setUnitPrice(unitPrice);
            item.setCostPriceSnapshot(product.getAvgCostPrice());
            item.setTotalPrice(itemTotal);
            item.setAllowSpecialFund(product.getAllowSpecialFund() != null ? product.getAllowSpecialFund() : 0);
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

        if (order.getOrderType() == 3) {
            OrderDelivery delivery = new OrderDelivery();
            delivery.setOrderId(order.getId());
            delivery.setDeliveryNo("DEL" + System.currentTimeMillis());
            delivery.setDeliveryAddress(order.getReceiverAddress());
            delivery.setDeliveryPhone(order.getReceiverPhone());
            delivery.setDeliveryType(deliveryType);
            delivery.setDeliveryStatus(1);
            delivery.setAuditStatus(1);
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setUpdatedAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
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
        Long operatorStaffId = createdBy != null ? createdBy : command.getStaffId();
        if (operatorStaffId == null) {
            throw new IllegalArgumentException("未获取到当前登录员工，无法创建POS订单");
        }
        if (createdBy != null && command.getStaffId() != null && !command.getStaffId().equals(createdBy)) {
            throw new IllegalArgumentException("POS下单不允许指定其他员工");
        }

        // 1. 获取当前操作员所属门店ID（关键：库存扣减必须基于当前登录员工所属门店）
        Staff staff = staffRepository.findById(operatorStaffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在: " + operatorStaffId));
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
        order.setStaffId(operatorStaffId);
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
            
            String itemUnit = StringUtils.hasText(itemDTO.getUnit()) ? itemDTO.getUnit() : product.getMainUnit();
            // 计算价格
            BigDecimal unitPrice = itemDTO.getUnitPrice();
            if (unitPrice == null) {
                unitPrice = productPricingService.resolveOrderItemUnitPrice(
                        product,
                        actualStoreId,
                        command.getCustomerId(),
                        itemUnit
                );
            }
            
            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity());
            totalAmount = totalAmount.add(itemTotal);
            
            // 创建订单明细
            OrderItem item = new OrderItem();
            item.setOrderId(null);
            item.setProductId(itemDTO.getProductId());
            item.setPackageId(itemDTO.getPackageId());
            item.setItemType(1); // 统一为商品类型
            item.setProductName(product.getName());
            item.setQuantity(itemDTO.getQuantity());
            item.setUnit(itemUnit);
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
                    command.getPaymentType().equals(OrderMainPaymentMethod.BALANCE.getCode()),
                    command.getUseSpecialFund(), 
                    command.getCouponId());
            couponAmount = preview.getCouponAmount();
            
            // 如果优惠券可用，执行核销
            if (couponAmount.compareTo(BigDecimal.ZERO) > 0) {
                // executePayment 会执行实际的扣减并保存支付流水，避免后续重复记录
                paymentService.executePayment(order, 
                        command.getPaymentType().equals(OrderMainPaymentMethod.BALANCE.getCode()),
                        command.getUseSpecialFund(), 
                        command.getCouponId(), 
                        createdBy,
                        true,
                        command.getPaymentType(),
                        null,
                        null);
                paymentsRecorded = true;
            }
        }
        
        // 8. 重新计算实付金额并更新订单
        BigDecimal payAmount = totalAmount.subtract(discountAmount).subtract(couponAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 实付金额不能小于0");
        }
        
        // ⛔️ 赊销订单二次校验：确保全额赊销
        BigDecimal creditAmount = BigDecimal.ZERO; // 赊销记账金额（在清零 payAmount 前保留）
        if (command.getPaymentType().equals(OrderMainPaymentMethod.CREDIT.getCode())) {
            if (payAmount.compareTo(totalAmount) != 0) {
                throw new IllegalArgumentException(OrderErrorCode.CREDIT_AMOUNT_MISMATCH +
                        ": 赊销订单必须全额赊销，实付金额必须等于订单总金额");
            }
            // 保留赊销记账金额 = 实付金额（即订单总额 - 折扣 - 优惠券），然后清零 payAmount（客户现场不付现金）
            creditAmount = payAmount;
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
                if (pt.equals(OrderMainPaymentMethod.BALANCE.getCode())) {
                    combinedBalanceRequested = combinedBalanceRequested.add(amt);
                    hasBalanceEntry = true;
                } else {
                    otherPayments.add(pd);
                }
            }

            // 先处理非余额类支付（赊销/现金/微信/支付宝/优惠券等）
            for (PaymentDetailDTO pd : otherPayments) {
                Integer pt = pd.getPaymentType();
                BigDecimal amt = pd.getAmount();
                if (pt.equals(OrderMainPaymentMethod.CREDIT.getCode())) {
                    if (order.getCustomerId() == null) {
                        throw new IllegalArgumentException("赊销必须指定客户");
                    }
                    creditService.addDebt(order.getCustomerId(), order.getId(), amt);
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(OrderMainPaymentMethod.CREDIT.getCode());
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
                if (order.getCustomerId() == null) {
                    throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 余额支付必须指定客户");
                }
                List<OrderItem> orderItemsForCalc = itemRepository.findByOrderId(order.getId());
                BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(orderItemsForCalc);
                BigDecimal customerSpecialFund = safeGetSpecialFundBalance(order.getCustomerId());
                BigDecimal customerBalance = safeGetBalance(order.getCustomerId());
                BigDecimal specialCanUse = maxSpecialFund.min(customerSpecialFund);
                BigDecimal walletCanPay = specialCanUse.add(customerBalance);
                if (combinedBalanceRequested.compareTo(walletCanPay) > 0) {
                    throw walletInsufficientException(walletCanPay);
                }
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
                // 忽略优惠券类型对主支付方式的影响
                if (pt.equals(OrderMainPaymentMethod.COUPON.getCode())) continue;
                paymentTypesUsed.add(pt);
            }
            int effectivePaymentMethod = command.getPaymentType();
            if (paymentTypesUsed.size() == 1) {
                effectivePaymentMethod = paymentTypesUsed.iterator().next();
            } else if (paymentTypesUsed.size() > 1) {
                effectivePaymentMethod = OrderMainPaymentMethod.MIXED.getCode();
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
        if (!paymentsProvided && ((command.getPaymentType().equals(OrderMainPaymentMethod.BALANCE.getCode())
                || command.getPaymentType().equals(OrderMainPaymentMethod.MIXED.getCode())
                || command.getWalletAuthCode() != null)
                && order.getCustomerId() != null)) {
            // 计算专用金和本金余额的分配（先算出将要从余额扣减的金额）
            List<OrderItem> orderItems = itemRepository.findByOrderId(order.getId());
            BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(orderItems);
            
            BigDecimal specialFundAmount = BigDecimal.ZERO;
            BigDecimal balanceAmount = BigDecimal.ZERO;
            BigDecimal payAmt = payAmount;
            BigDecimal customerBalance = BigDecimal.ZERO;

            // 如果使用专用金，计算专用金金额并减去
            if (Boolean.TRUE.equals(command.getUseSpecialFund())) {
                BigDecimal customerSpecialFund = safeGetSpecialFundBalance(order.getCustomerId());
                specialFundAmount = payAmt.min(maxSpecialFund).min(customerSpecialFund);
            }
            BigDecimal remainingAfterSpecial = payAmt.subtract(specialFundAmount);

            // 计算可从本金余额中扣减的金额（不超过客户余额）
            logger.info("About to query customer balance for customerId={} orderId={}", order.getCustomerId(), order.getId());
            customerBalance = safeGetBalance(order.getCustomerId());
            logger.info("Queried customer balance for customerId={} balance={}", order.getCustomerId(), customerBalance);
            balanceAmount = remainingAfterSpecial.min(customerBalance);

            if (command.getPaymentType().equals(OrderMainPaymentMethod.BALANCE.getCode())) {
                BigDecimal walletCanPay = specialFundAmount.add(customerBalance);
                if (payAmt.compareTo(walletCanPay) > 0) {
                    throw walletInsufficientException(walletCanPay);
                }
            }

            // 如果需要从本金余额扣减，则必须提供钱包验证码
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                if (!StringUtils.hasText(command.getWalletAuthCode())) {
                    throw new IllegalArgumentException(OrderErrorCode.WALLET_AUTH_CODE_INVALID + ": 钱包验证码不能为空");
                }
                BigDecimal normalizedBalanceAmount = balanceAmount.setScale(2, RoundingMode.HALF_UP);
                int updated = walletAuthCodeRepository.consumeAuthCode(
                        order.getCustomerId(),
                        command.getWalletAuthCode(),
                        normalizedBalanceAmount,
                        order.getId()
                );
                if (updated == 0) {
                    throw new IllegalStateException("验证码核销失败：验证码无效、已过期、金额不匹配或已被使用");
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
        // 使用前面保留的 creditAmount（订单总额-折扣-优惠券），而非 order.getPayAmount()（赊销订单已清零为0）
        if (command.getPaymentType().equals(OrderMainPaymentMethod.CREDIT.getCode()) && order.getCustomerId() != null) {
            creditService.addDebt(order.getCustomerId(), order.getId(), creditAmount);
        }

        // 13. 发布订单完成事件（用于更新客户统计和发放积分）
        eventPublisher.publishEvent(new OrderFinishedEvent(order.getId()));

        return order.getId();
    }
    
    /**
     * 订单支付（小程序兼容入口）
     *
     * 仅当订单无需外部支付时，直接完成内部支付并落单。
     * 若仍需微信/支付宝，则只返回预览，由前端继续走 payment-intent / payment-confirmation。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPayPreviewVO payOrder(OrderPayCommand command, Long customerId) {
        Order order = loadPendingCustomerOrder(command.getOrderId(), customerId);
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
    @Transactional(rollbackFor = Exception.class)
    public void confirmExternalPayment(OrderExternalPaymentConfirmCommand command, Long customerId) {
        OrderPaymentIntent intent = loadPaymentIntent(command.getIntentNo());
        if (intent.getCustomerId() == null || !intent.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权确认此支付");
        }
        confirmExternalPaymentIntent(intent, command.getExternalTransactionNo(), command.getExternalPaidAt(), customerId);
    }

    /**
     * 外部支付回调确认
     */
    @Transactional(rollbackFor = Exception.class)
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
            List<OrderPayment> payments = paymentRepository.findByOrderId(order.getId());
            PaymentSummary paymentSummary = summarizePayment(order, payments);
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStoreId(order.getStoreId());
            vo.setCustomerId(order.getCustomerId());
            vo.setOrderType(order.getOrderType());
            vo.setDeliveryType(resolveDeliveryType(order));
            vo.setOrderStatus(order.getOrderStatus());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setDiscountAmount(order.getDiscountAmount());
            vo.setCouponAmount(order.getCouponAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setReceivedAmount(paymentSummary.receivedAmount);
            vo.setCreditAmount(paymentSummary.creditAmount);
            vo.setReceiverName(order.getReceiverName());
            vo.setReceiverPhone(order.getReceiverPhone());
            vo.setReceiverAddress(order.getReceiverAddress());
            vo.setPickupCode(order.getPickupCode());
            vo.setCreatedAt(order.getCreatedAt());
            
            // 查询门店名称
            if (order.getStoreId() != null) {
                storeRepository.findByIdIncludingDeleted(order.getStoreId()).ifPresent(store -> {
                    vo.setStoreName(store.getName());
                });
            }
            
            // 查询客户信息
            if (order.getCustomerId() != null) {
                customerRepository.findByIdIncludingDeleted(order.getCustomerId()).ifPresent(customer -> {
                    vo.setCustomerName(customer.getName());
                    vo.setCustomerPhone(customer.getPhone());
                });
            }
            
            // 主支付方式（若记录在订单上），若订单表未记录则回退到支付流水的第一条记录
            if (order.getPaymentMethod() != null) {
                vo.setPaymentMethod(order.getPaymentMethod());
                vo.setPaymentMethodName(resolveMainPaymentMethodName(order.getPaymentMethod()));
            } else {
                if (payments != null && !payments.isEmpty()) {
                    OrderPayment firstPayment = payments.get(0);
                    vo.setPaymentMethod(firstPayment.getPaymentType());
                    vo.setPaymentMethodName(resolvePaymentRecordDisplayName(firstPayment));
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

    private Integer resolveDeliveryType(Order order) {
        if (order == null || order.getOrderType() == null) {
            return null;
        }
        if (order.getOrderType() == 2) {
            return 1;
        }
        if (order.getOrderType() != 3) {
            return null;
        }
        return deliveryRepository.findByOrderId(order.getId())
                .map(this::resolveDeliveryType)
                .orElse(3);
    }

    private Integer resolveDeliveryType(OrderDelivery delivery) {
        if (delivery == null) {
            return 3;
        }
        if (delivery.getDeliveryType() != null) {
            return delivery.getDeliveryType();
        }
        if (delivery.getDeliveryStaffId() != null) {
            return 2;
        }
        return 3;
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
        java.util.Map<Long, RefundAggregate> refundAggregates = loadOrderItemRefundAggregates(orderId);
        
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
        PaymentSummary paymentSummary = summarizePayment(order, payments);
        vo.setReceivedAmount(paymentSummary.receivedAmount);
        vo.setCreditAmount(paymentSummary.creditAmount);
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaymentMethodName(resolveMainPaymentMethodName(order.getPaymentMethod()));
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
            RefundAggregate refundAggregate = refundAggregates.get(item.getId());
            BigDecimal refundedQuantity = refundAggregate != null ? refundAggregate.quantity : BigDecimal.ZERO;
            BigDecimal refundedAmount = refundAggregate != null ? refundAggregate.amount : BigDecimal.ZERO;
            itemVO.setRefundedQuantity(refundedQuantity);
            itemVO.setRefundedAmount(refundedAmount);
            itemVO.setRefundableQuantity(safeSubtract(item.getQuantity(), refundedQuantity));
            itemVO.setRefundableAmount(safeSubtract(item.getTotalPrice(), refundedAmount));
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
            paymentVO.setPaymentTypeName(resolvePaymentRecordDisplayName(payment));
            paymentVO.setAmount(payment.getAmount());
            paymentVO.setCouponId(payment.getCouponId());
            paymentVO.setOuterTransNo(payment.getOuterTransNo());
            paymentVO.setCreatedAt(payment.getCreatedAt());
            return paymentVO;
        }).collect(Collectors.toList()));
        
        // 查询门店名称
        if (order.getStoreId() != null) {
            storeRepository.findByIdIncludingDeleted(order.getStoreId()).ifPresent(store -> {
                vo.setStoreName(store.getName());
            });
        }
        
        // 查询客户信息
        if (order.getCustomerId() != null) {
            customerRepository.findByIdIncludingDeleted(order.getCustomerId()).ifPresent(customer -> {
                vo.setCustomerName(customer.getName());
                vo.setCustomerPhone(customer.getPhone());
            });
        }
        
        // 查询配送信息
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            OrderDeliveryVO deliveryVO = new OrderDeliveryVO();
            deliveryVO.setLogisticsCompany(delivery.getLogisticsCompany());
            deliveryVO.setTrackingNo(delivery.getTrackingNo());
            deliveryVO.setDeliveryStatus(delivery.getDeliveryStatus());
            deliveryVO.setDeliveryType(resolveDeliveryType(delivery));
            deliveryVO.setDeliveryStaffId(delivery.getDeliveryStaffId());
            deliveryVO.setDeliveryAt(delivery.getDeliveryAt());
            if (delivery.getDeliveryStaffId() != null) {
                staffRepository.findById(delivery.getDeliveryStaffId())
                        .filter(Staff::isActive)
                        .filter(staff -> !staff.hasBeenDeleted())
                        .ifPresent(staff -> deliveryVO.setDeliveryStaffName(staff.getName()));
            }
            vo.setDelivery(deliveryVO);
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

    public OrderDetailVO getOrderDetailForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        if (order.getCustomerId() == null || !order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权查看此订单");
        }
        return getOrderDetail(orderId);
    }

    private java.util.Map<Long, RefundAggregate> loadOrderItemRefundAggregates(Long orderId) {
        java.util.Map<Long, RefundAggregate> aggregates = new java.util.HashMap<>();
        List<com.agri_system.domain.after_sales.model.AfterSales> afterSalesList = afterSalesRepository.findByOrderId(orderId);
        if (afterSalesList == null || afterSalesList.isEmpty()) {
            return aggregates;
        }

        for (com.agri_system.domain.after_sales.model.AfterSales afterSales : afterSalesList) {
            if (afterSales == null
                    || afterSales.getStatus() == com.agri_system.domain.after_sales.model.AfterSales.STATUS_REJECTED) {
                continue;
            }
            List<com.agri_system.domain.after_sales.model.AfterSalesItem> refundItems =
                    afterSalesItemRepository.findByAfterSalesId(afterSales.getId());
            if (refundItems == null || refundItems.isEmpty()) {
                continue;
            }
            for (com.agri_system.domain.after_sales.model.AfterSalesItem refundItem : refundItems) {
                if (refundItem == null || refundItem.getOrderItemId() == null) {
                    continue;
                }
                RefundAggregate aggregate = aggregates.computeIfAbsent(refundItem.getOrderItemId(),
                        key -> new RefundAggregate());
                aggregate.quantity = aggregate.quantity.add(defaultAmount(refundItem.getQuantity()));
                aggregate.amount = aggregate.amount.add(defaultAmount(refundItem.getRefundAmount()));
            }
        }
        return aggregates;
    }

    private BigDecimal safeSubtract(BigDecimal total, BigDecimal used) {
        BigDecimal result = defaultAmount(total).subtract(defaultAmount(used));
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private PaymentSummary summarizePayment(Order order, List<OrderPayment> payments) {
        BigDecimal receivedAmount = BigDecimal.ZERO;
        BigDecimal creditAmount = BigDecimal.ZERO;

        if (payments != null && !payments.isEmpty()) {
            for (OrderPayment payment : payments) {
                if (payment == null || payment.getAmount() == null) {
                    continue;
                }
                Integer paymentType = payment.getPaymentType();
                if (paymentType != null && paymentType == PaymentMethod.COUPON.getCode()) {
                    continue;
                }
                if (paymentType != null && paymentType == PaymentMethod.CREDIT.getCode()) {
                    creditAmount = creditAmount.add(payment.getAmount());
                } else {
                    receivedAmount = receivedAmount.add(payment.getAmount());
                }
            }
            return new PaymentSummary(receivedAmount, creditAmount);
        }

        BigDecimal netReceivable = safeSubtract(
                safeSubtract(order.getTotalAmount(), order.getDiscountAmount()),
                order.getCouponAmount()
        );

        if (order.getPaymentMethod() != null && order.getPaymentMethod() == OrderMainPaymentMethod.CREDIT.getCode()) {
            return new PaymentSummary(BigDecimal.ZERO, netReceivable);
        }

        receivedAmount = defaultAmount(order.getPayAmount());
        creditAmount = safeSubtract(netReceivable, receivedAmount);
        return new PaymentSummary(receivedAmount, creditAmount);
    }

    private static class RefundAggregate {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    private static class PaymentSummary {
        private final BigDecimal receivedAmount;
        private final BigDecimal creditAmount;

        private PaymentSummary(BigDecimal receivedAmount, BigDecimal creditAmount) {
            this.receivedAmount = receivedAmount == null ? BigDecimal.ZERO : receivedAmount;
            this.creditAmount = creditAmount == null ? BigDecimal.ZERO : creditAmount;
        }
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

        Order order = orderRepository.findById(intent.getOrderId())
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
        paymentService.executePayment(
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

    private String resolveMainPaymentMethodName(Integer paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.CASH.getCode())) {
            return "现金";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.WECHAT.getCode())) {
            return "微信";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.ALIPAY.getCode())) {
            return "支付宝";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.BALANCE.getCode())) {
            return "本金余额";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.COUPON.getCode())) {
            return "优惠券";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.CREDIT.getCode())) {
            return "赊销";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.MIXED.getCode())) {
            return "混合支付";
        }
        return "未知";
    }

    private String resolvePaymentRecordDisplayName(OrderPayment payment) {
        Integer paymentType = payment.getPaymentType();
        if (paymentType == null) {
            return "未知";
        }
        if (paymentType.equals(PaymentMethod.CASH.getCode())) {
            return "现金";
        }
        if (paymentType.equals(PaymentMethod.WECHAT.getCode())) {
            return "微信";
        }
        if (paymentType.equals(PaymentMethod.ALIPAY.getCode())) {
            return "支付宝";
        }
        if (paymentType.equals(PaymentMethod.BALANCE.getCode())) {
            return "本金余额";
        }
        if (paymentType.equals(PaymentMethod.SPECIAL_FUND.getCode())) {
            return "专用金";
        }
        if (paymentType.equals(PaymentMethod.COUPON.getCode())) {
            return "优惠券";
        }
        if (paymentType.equals(PaymentMethod.CREDIT.getCode())) {
            return "赊销";
        }
        if (paymentType.equals(PaymentMethod.MIXED.getCode())) {
            return "混合支付";
        }
        return "未知";
    }

    private BigDecimal safeGetBalance(Long customerId) {
        if (customerId == null) {
            return BigDecimal.ZERO;
        }
        try {
            return walletService.getBalance(customerId);
        } catch (Exception e) {
            logger.error("Failed to query balance for customerId={}", customerId, e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeGetSpecialFundBalance(Long customerId) {
        if (customerId == null) {
            return BigDecimal.ZERO;
        }
        try {
            return walletService.getSpecialFundBalance(customerId);
        } catch (Exception e) {
            logger.error("Failed to query special fund for customerId={}", customerId, e);
            return BigDecimal.ZERO;
        }
    }

    private IllegalArgumentException walletInsufficientException(BigDecimal walletCanPay) {
        return new IllegalArgumentException(
                OrderErrorCode.ORDER_AMOUNT_INVALID + ": 钱包可支付金额不足，当前最多可支付 ¥"
                        + walletCanPay.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private void finalizeClientOrderPayment(Order order, Long customerId) {
        order.markAsPaid();
        if (order.getOrderType() == 2) {
            order.markAsWaitPickup();
        } else if (order.getOrderType() == 3) {
            order.markAsWaitShip();
        }
        orderRepository.save(order);

        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            InventoryOutCommand outCommand = new InventoryOutCommand();
            outCommand.setStoreId(order.getStoreId());
            outCommand.setProductId(item.getProductId());
            outCommand.setQuantity(item.getQuantity());
            outCommand.setUnit(item.getUnit());
            outCommand.setReferenceType("ORDER");
            outCommand.setReferenceId(order.getId());
            inventoryService.outbound(outCommand, customerId);
        }
    }

    private void restockOrderItemsForRefund(Order order, Long createdBy) {
        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));

            InventoryInCommand inCommand = new InventoryInCommand();
            inCommand.setStoreId(order.getStoreId());
            inCommand.setProductId(item.getProductId());
            inCommand.setBatchNo(buildRefundBatchNo(order, item));
            inCommand.setQuantity(normalizeRefundQuantity(item, product));
            inCommand.setCostPrice(resolveRefundCostPrice(item));
            inCommand.setSourceType("ORDER_REFUND");
            inCommand.setSourceId(order.getId());
            inCommand.setSourceLineNo(Math.toIntExact(item.getId()));
            inCommand.setRemark("订单退款回库");
            inventoryService.inbound(inCommand, createdBy);
        }
    }

    private void restockOrderItemsForRefund(Order order, List<RefundOrderItem> refundItems, Long createdBy) {
        if (refundItems == null || refundItems.isEmpty()) {
            return;
        }
        for (RefundOrderItem refundItem : refundItems) {
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
            inCommand.setQuantity(normalizeRefundQuantity(item, product, refundItem.quantity()));
            inCommand.setCostPrice(resolveRefundCostPrice(item));
            inCommand.setSourceType("ORDER_REFUND");
            inCommand.setSourceId(order.getId());
            inCommand.setSourceLineNo(Math.toIntExact(item.getId()));
            inCommand.setRemark("订单部分退款回库");
            inventoryService.inbound(inCommand, createdBy);
        }
    }

    private BigDecimal normalizeRefundQuantity(OrderItem item, Product product) {
        return normalizeRefundQuantity(item, product, item.getQuantity());
    }

    private BigDecimal normalizeRefundQuantity(OrderItem item, Product product, BigDecimal quantity) {
        if (item.getUnit() != null
                && product.getAuxUnit() != null
                && item.getUnit().equals(product.getAuxUnit())
                && product.getMainToAuxRatio() != null
                && product.getMainToAuxRatio().compareTo(BigDecimal.ONE) > 0) {
            return quantity.divide(product.getMainToAuxRatio(), 8, RoundingMode.HALF_UP);
        }
        return quantity;
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
        BigDecimal refundAmount = command.getRefundAmount();
        List<OrderRefundCommand.RefundItem> commandItems = command.getItems();
        boolean hasItemDetails = commandItems != null && !commandItems.isEmpty();

        if (!hasItemDetails) {
            if (refundAmount == null) {
                refundAmount = order.getPayAmount();
            }
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额必须大于0");
            }
            if (refundAmount.compareTo(order.getPayAmount()) > 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额不能超过订单实付金额");
            }
            if (refundAmount.compareTo(order.getPayAmount()) < 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 部分退款必须指定退款商品明细");
            }
            List<RefundOrderItem> refundItems = orderItems.stream()
                    .filter(item -> item.getId() != null && item.getQuantity() != null)
                    .map(item -> new RefundOrderItem(item, item.getQuantity(), defaultAmount(item.getTotalPrice())))
                    .toList();
            return new RefundPlan(refundAmount, refundItems, true);
        }

        Map<Long, OrderItem> orderItemMap = orderItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(OrderItem::getId, item -> item));
        Map<Long, RefundAggregate> existingRefunds = loadOrderItemRefundAggregates(order.getId());
        Map<Long, RefundAggregate> requestedRefunds = new HashMap<>();
        List<RefundOrderItem> refundItems = new ArrayList<>();
        BigDecimal totalRefundAmount = BigDecimal.ZERO;

        for (OrderRefundCommand.RefundItem commandItem : commandItems) {
            OrderItem orderItem = Optional.ofNullable(orderItemMap.get(commandItem.getOrderItemId()))
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单明细不存在"));

            RefundAggregate aggregate = existingRefunds.get(orderItem.getId());
            RefundAggregate requestedAggregate = requestedRefunds.computeIfAbsent(orderItem.getId(), key -> new RefundAggregate());
            BigDecimal refundedQuantity = aggregate != null ? aggregate.quantity : BigDecimal.ZERO;
            BigDecimal refundedAmount = aggregate != null ? aggregate.amount : BigDecimal.ZERO;
            BigDecimal refundableQuantity = safeSubtract(orderItem.getQuantity(), refundedQuantity);
            BigDecimal refundableAmount = safeSubtract(resolveOrderItemTotalAmount(orderItem), refundedAmount);

            BigDecimal cumulativeRequestedQuantity = requestedAggregate.quantity.add(commandItem.getQuantity());
            BigDecimal cumulativeRequestedAmount = requestedAggregate.amount.add(commandItem.getRefundAmount());
            if (cumulativeRequestedQuantity.compareTo(refundableQuantity) > 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款数量不能超过当前可退数量");
            }
            if (cumulativeRequestedAmount.compareTo(refundableAmount) > 0) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 明细退款金额不能超过当前可退金额");
            }

            requestedAggregate.quantity = cumulativeRequestedQuantity;
            requestedAggregate.amount = cumulativeRequestedAmount;
            refundItems.add(new RefundOrderItem(orderItem, commandItem.getQuantity(), commandItem.getRefundAmount()));
            totalRefundAmount = totalRefundAmount.add(commandItem.getRefundAmount());
        }

        if (refundAmount == null) {
            refundAmount = totalRefundAmount;
        } else if (refundAmount.compareTo(totalRefundAmount) != 0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 退款明细金额之和与退款总金额不一致");
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额必须大于0");
        }
        if (refundAmount.compareTo(order.getPayAmount()) > 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 退款金额不能超过订单实付金额");
        }

        boolean fullRefund = refundAmount.compareTo(order.getPayAmount()) >= 0;
        return new RefundPlan(refundAmount, refundItems, fullRefund);
    }

    private BigDecimal resolveOrderItemTotalAmount(OrderItem orderItem) {
        BigDecimal totalPrice = defaultAmount(orderItem.getTotalPrice());
        if (totalPrice.compareTo(BigDecimal.ZERO) > 0) {
            return totalPrice;
        }
        if (orderItem.getUnitPrice() != null && orderItem.getQuantity() != null) {
            return orderItem.getUnitPrice().multiply(orderItem.getQuantity());
        }
        return BigDecimal.ZERO;
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

    private String buildOrderRefundEventKey(Long orderId, RefundPlan refundPlan) {
        StringBuilder builder = new StringBuilder("ORDER_REFUND:").append(orderId);
        for (RefundOrderItem refundItem : refundPlan.refundItems()) {
            if (refundItem.orderItem().getId() == null) {
                continue;
            }
            builder.append(":")
                    .append(refundItem.orderItem().getId())
                    .append("=")
                    .append(refundItem.quantity())
                    .append("@")
                    .append(refundItem.refundAmount());
        }
        return builder.toString();
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
        deliveryRepository.findByOrderId(orderId).ifPresent(delivery -> {
            delivery.setDeliveryStatus(4);
            delivery.setReceivedAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
        });
        eventPublisher.publishEvent(new OrderFinishedEvent(order.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmReceiptForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
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

        if (command.getDeliveryStaffId() != null) {
            Staff deliveryStaff = staffRepository.findById(command.getDeliveryStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("配送员不存在"));
            if (!deliveryStaff.isActive() || deliveryStaff.hasBeenDeleted()) {
                throw new IllegalArgumentException("配送员不是有效的在职员工");
            }
            delivery.setDeliveryType(2);
            delivery.setDeliveryStaffId(deliveryStaff.getId());
            delivery.setLogisticsCompany(null);
            delivery.setTrackingNo(null);
        } else {
            if (!StringUtils.hasText(command.getLogisticsCompany()) || !StringUtils.hasText(command.getTrackingNo())) {
                throw new IllegalArgumentException("快递发货必须填写物流公司和物流单号");
            }
            delivery.setDeliveryType(3);
            delivery.setDeliveryStaffId(null);
            delivery.setLogisticsCompany(command.getLogisticsCompany().trim());
            delivery.setTrackingNo(command.getTrackingNo().trim());
        }
        delivery.setDeliveryStatus(3); // 配送中
        delivery.setDeliveryAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
    }

    private Integer validateAndResolveCreateDeliveryType(OrderCreateCommand command) {
        if (command.getOrderType() == null || (command.getOrderType() != 2 && command.getOrderType() != 3)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 小程序订单类型仅支持自提或配送");
        }
        if (command.getOrderType() == 2) {
            if (command.getDeliveryType() != null && command.getDeliveryType() != 1) {
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 自提订单不支持同城或快递配送方式");
            }
            return 1;
        }
        if (!StringUtils.hasText(command.getDeliveryAddress()) || !StringUtils.hasText(command.getDeliveryPhone())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 配送订单必须填写收货地址和联系电话");
        }
        if (command.getDeliveryType() == null) {
            return 3;
        }
        if (command.getDeliveryType() != 2 && command.getDeliveryType() != 3) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 配送方式仅支持同城配送或快递配送");
        }
        return command.getDeliveryType();
    }

    private Store validateAndLoadCreateStore(OrderCreateCommand command) {
        Store store = storeRepository.findById(command.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 门店不存在或已删除"));
        if (!store.isEnabled()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 门店已停用，暂不可下单");
        }
        if (command.getOrderType() != null && command.getOrderType() == 2 && !store.supportsPickup()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 当前门店不支持自提");
        }
        return store;
    }

    private Customer loadCreateOrderCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 客户不存在或已删除"));
        if (!customer.isActive()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 客户账号已停用，暂不可下单");
        }
        return customer;
    }

    private String resolveCreateOrderReceiverName(OrderCreateCommand command, Customer customer) {
        String receiverName = StringUtils.hasText(command.getReceiverName())
                ? command.getReceiverName().trim()
                : null;
        if (!StringUtils.hasText(receiverName) && customer != null && StringUtils.hasText(customer.getName())) {
            receiverName = customer.getName().trim();
        }
        if (command.getOrderType() != null && command.getOrderType() == 3 && !StringUtils.hasText(receiverName)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 配送订单必须填写收货人姓名");
        }
        return receiverName;
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
        List<OrderItem> orderItems = itemRepository.findByOrderId(order.getId());
        RefundPlan refundPlan = buildRefundPlan(order, orderItems, command);
        // 执行退款
        paymentService.refund(order, refundPlan.refundAmount(), createdBy);

        if (refundPlan.fullRefund()) {
            restockOrderItemsForRefund(order, createdBy);
        } else {
            restockOrderItemsForRefund(order, refundPlan.refundItems(), createdBy);
        }
        
        // 更新订单状态
        if (refundPlan.fullRefund()) {
            order.markAsRefunded();
        } else {
            order.markAsPartRefunded();
        }
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderRefundedEvent(order.getId(),
                refundPlan.refundAmount(),
                extractRefundItemQuantities(refundPlan.refundItems()),
                buildOrderRefundEventKey(order.getId(), refundPlan)));
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
    private void savePaymentRecords(Order order, POSOrderCreateCommand command, Long createdBy) {
        LocalDateTime now = LocalDateTime.now();
        // existing payments to avoid duplicates
        List<OrderPayment> existingPayments = paymentRepository.findByOrderId(order.getId());
        
        // 赊销订单：只记录一条赊销记录（金额 = 订单应收 - 折扣 - 优惠券）
        if (command.getPaymentType().equals(OrderMainPaymentMethod.CREDIT.getCode())) {
            BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
            BigDecimal coupon = order.getCouponAmount() == null ? BigDecimal.ZERO : order.getCouponAmount();
            BigDecimal creditAmount = total.subtract(discount).subtract(coupon);
            if (creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                boolean dup = existingPayments.stream().anyMatch(p -> p.getPaymentType() != null
                        && p.getPaymentType().equals(OrderMainPaymentMethod.CREDIT.getCode())
                        && p.getAmount() != null && p.getAmount().compareTo(creditAmount) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(OrderMainPaymentMethod.CREDIT.getCode()); // 赊销
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
                        && p.getPaymentType().equals(PaymentMethod.SPECIAL_FUND.getCode())
                        && p.getAmount() != null && p.getAmount().compareTo(sfAmt) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(PaymentMethod.SPECIAL_FUND.getCode());
                    payment.setAmount(specialFundAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
        }
        
        // 本金余额抵扣（余额支付时，或混合支付且使用余额时，扣除专用金后的剩余部分）
        if ((command.getPaymentType().equals(OrderMainPaymentMethod.BALANCE.getCode())
                || command.getPaymentType().equals(OrderMainPaymentMethod.MIXED.getCode())
                || command.getWalletAuthCode() != null) && order.getCustomerId() != null) {
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
                        && p.getPaymentType().equals(PaymentMethod.BALANCE.getCode())
                        && p.getAmount() != null && p.getAmount().compareTo(balAmt) == 0);
                if (!dup) {
                    OrderPayment payment = new OrderPayment();
                    payment.setOrderId(order.getId());
                    payment.setPaymentType(PaymentMethod.BALANCE.getCode());
                    payment.setAmount(balanceAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
        }
        
        // 外部支付（现金、微信、支付宝）
        // 只有在非余额支付且非混合支付时才记录外部支付（混合支付不应保存一条总额为混合的记录）
        if (command.getPaymentType() != OrderMainPaymentMethod.BALANCE.getCode()
                && command.getPaymentType() != OrderMainPaymentMethod.MIXED.getCode()) {
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
                    payment.setPaymentType(command.getPaymentType());
                    payment.setAmount(externalAmount);
                    payment.setCreatedAt(now);
                    paymentRepository.save(payment);
                }
            }
        }
    }

}
