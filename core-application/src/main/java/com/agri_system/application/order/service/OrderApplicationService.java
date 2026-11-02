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

    @Value("${payment.client-confirm.enabled:false}")
    private boolean clientPaymentConfirmEnabled;
    
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
            if (itemDTO.getPackageId() != null || Integer.valueOf(2).equals(itemDTO.getItemType())) {
                throw new IllegalArgumentException("套餐销售价格尚未统一，请按单品开单");
            }
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));
            
            String itemUnit = StringUtils.hasText(itemDTO.getUnit()) ? itemDTO.getUnit() : product.getMainUnit();
            inventoryService.validateSaleQuantity(product.getId(), itemUnit, itemDTO.getQuantity());
            // 客户端下单不信任前端传入单价，统一由后端价格体系计算
            BigDecimal unitPrice = productPricingService.resolveOrderItemUnitPrice(
                    product,
                    order.getStoreId(),
                    order.getCustomerId(),
                    itemUnit
            );
            
            BigDecimal itemTotal = unitPrice.multiply(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (itemTotal.signum() <= 0) throw new IllegalArgumentException("明细金额不足一分，请调整销售数量");
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
     * 创建POS订单
     * 
     * @param command POS订单创建命令
     * @param createdBy 操作人ID
     * @return 订单ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long createPOSOrder(POSOrderCreateCommand command, Long createdBy) {
        int deliveryType = validatePosDelivery(command);
        validatePosPayments(command);
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
        
        // 先锁客户，随后才写订单外键及库存；与还款/退货的客户→账户→库存顺序一致。
        if(command.getCustomerId()!=null) customerRepository.findByIdWithLock(command.getCustomerId())
                .orElseThrow(()->new IllegalArgumentException("订单客户不存在"));

        BigDecimal manualDiscount = command.getDiscountAmount();
        if (manualDiscount != null && (manualDiscount.signum() < 0 || manualDiscount.scale() > 2)) {
            throw new IllegalArgumentException("整单优惠不能为负且最多两位小数");
        }
        if (manualDiscount != null && manualDiscount.signum() > 0) {
            manualPriceAuditService.validateDiscount(manualDiscount, command.getRemark(), operatorStaffId);
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
        order.setOrderStatus(deliveryType == 2 ? 4 : 6);
        if (deliveryType == 2) {
            order.setReceiverName(command.getReceiverName().trim());
            order.setReceiverPhone(command.getReceiverPhone().trim());
            order.setReceiverAddress(command.getReceiverAddress().trim());
        }
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setStaffId(operatorStaffId);
        order.setRemark(manualDiscount != null && manualDiscount.signum() > 0
                ? "整单优惠原因：" + command.getRemark().trim() : command.getRemark());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setCreatedBy(createdBy);
        
        // 5. 创建订单明细并计算总金额
        // 套餐由后端展开为物料行，成交金额使用套餐分摊结果。
        List<OrderItem> items = new ArrayList<>();
        List<InventoryOutCommand> inventoryOutCommands = new ArrayList<>();
        List<PendingPriceChange> pendingPriceChanges = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortOrder = 0;

        // 价格分层取自订单客户档案（无客户时为通用档），报价渠道固定 POS、业务日期为当日
        ProductPricingService.CustomerPriceContext customerContext =
                productPricingService.resolveCustomerContext(command.getCustomerId());
        Integer quoteCustomerType = customerContext != null ? customerContext.customerType() : null;
        Integer quoteVipLevel = customerContext != null ? customerContext.vipLevel() : null;
        
        Map<OrderItemDTO, PackageLineContext> packageLines = new java.util.IdentityHashMap<>();
        List<OrderItemDTO> pricedInputs = expandPackageSales(command.getItems(), actualStoreId,
                command.getCustomerId(), operatorStaffId, packageLines);
        for (OrderItemDTO itemDTO : pricedInputs) {
            PackageLineContext packageLine = packageLines.get(itemDTO);
            // 校验必填字段
            if (itemDTO.getProductId() == null) {
                throw new IllegalArgumentException("商品ID不能为空");
            }
            
            // 查询商品信息
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 商品不存在"));
            
            String itemUnit = StringUtils.hasText(itemDTO.getUnit()) ? itemDTO.getUnit() : product.getMainUnit();
            inventoryService.validateSaleQuantity(product.getId(), itemUnit, itemDTO.getQuantity());
            PriceQuote quote;
            BigDecimal unitPrice;
            BigDecimal itemTotal;
            boolean changedPrice;
            boolean belowFloor;
            if (packageLine != null) {
                var allocation = packageLine.material();
                unitPrice = allocation.unitPrice();
                itemTotal = allocation.totalPrice();
                quote = new PriceQuote(packageLine.quote().quoteId(), null, product.getId(), actualStoreId, "POS",
                        quoteCustomerType, null, itemUnit, allocation.saleUnitId(), LocalDate.now(),
                        allocation.suggestedUnitPrice(), "PACKAGE", null, false, false);
                changedPrice = packageLine.quote().dealUnitPrice().compareTo(packageLine.quote().suggestedUnitPrice()) != 0;
                belowFloor = packageLine.quote().belowReference();
            } else {
                quote = productPricingService.quote(new PriceContext(product.getId(), actualStoreId, "POS",
                        quoteCustomerType, quoteVipLevel, itemUnit, LocalDate.now()));
                if (itemDTO.getQuotedUnitPrice() != null
                        && itemDTO.getQuotedUnitPrice().compareTo(quote.suggestedUnitPrice()) != 0) {
                    throw new IllegalArgumentException("商品价格已变化，请重新报价后结算: " + product.getName());
                }
                unitPrice = itemDTO.getUnitPrice() != null ? itemDTO.getUnitPrice() : quote.suggestedUnitPrice();
                changedPrice = unitPrice.compareTo(quote.suggestedUnitPrice()) != 0;
                belowFloor = quote.referenceFloor() != null && unitPrice.compareTo(quote.referenceFloor()) < 0;
                if (changedPrice || belowFloor) {
                    manualPriceAuditService.validateChange(quote, unitPrice, itemDTO.getPriceChangeReason(), operatorStaffId);
                }
                itemTotal = unitPrice.multiply(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                if (itemTotal.signum() <= 0) throw new IllegalArgumentException("明细金额不足一分，请调整销售数量");
            }
            totalAmount = totalAmount.add(itemTotal);
            
            // 创建订单明细
            OrderItem item = new OrderItem();
            item.setOrderId(null);
            item.setProductId(itemDTO.getProductId());
            item.setPackageId(itemDTO.getPackageId());
            if (packageLine != null) {
                item.setPackageGroupNo(packageLine.groupNo());
                item.setPackageName(packageLine.quote().packageName());
                item.setPackageQuantity(packageLine.quote().quantity());
                item.setPackageUnitPrice(packageLine.quote().dealUnitPrice());
            }

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

            // 提交价与报价不一致：明细保存拿到 orderItemId 后补记人工改价审计（保留人工成交价）
            if (changedPrice || belowFloor) {
                pendingPriceChanges.add(new PendingPriceChange(item, quote, unitPrice,
                        itemDTO.getPriceChangeReason(), packageLine != null));
            }
            
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
        
        // 6. 计算订单金额（总额、优惠、优惠券、实付）
        BigDecimal discountAmount = command.getDiscountAmount() != null ? command.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal couponAmount = BigDecimal.ZERO;
        
        // FK插入订单明细会持有product父行共享锁；两单先插行再升级库存排他锁会互锁。
        // 已先锁客户并按ID锁完套餐，此处按商品ID取得库存锁后才允许写明细。
        // 新单应收尚未发布；同客户的原账户/钱包竞争仍由前置客户锁串行化。
        inventoryService.lockProducts(actualStoreId, inventoryOutCommands.stream().map(InventoryOutCommand::getProductId).toList());

        // 先保存订单和明细（用于优惠券校验）
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setCouponAmount(BigDecimal.ZERO); // 临时设为0，后续计算
        order.setPayAmount(totalAmount.subtract(discountAmount)); // 临时计算，后续更新
        order.setPaymentMethod(command.getPaymentType());
        
        order = orderRepository.save(order);
        
        if (deliveryType == 2) {
            OrderDelivery delivery = new OrderDelivery();
            delivery.setOrderId(order.getId());
            delivery.setDeliveryType(2);
            delivery.setDeliveryStatus(2);
            delivery.setDeliveryAddress(order.getReceiverAddress());
            delivery.setDeliveryPhone(order.getReceiverPhone());
            delivery.setCreatedAt(order.getCreatedAt());
            delivery.setUpdatedAt(order.getUpdatedAt());
            deliveryRepository.save(delivery);
        }

        // 保存订单明细
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            itemRepository.save(item);
        }

        // 人工改价只留痕不拒单：明细落库拿到 orderItemId 后写入价格审计（与建议价差价、原因、操作人）
        for (PendingPriceChange pending : pendingPriceChanges) {
            PriceQuote savedQuote = pending.quote().withOrderItemId(pending.item().getId());
            if (pending.packageAllocation()) {
                manualPriceAuditService.recordAllocation(savedQuote, pending.submittedUnitPrice(), pending.reason(), operatorStaffId);
            } else {
                manualPriceAuditService.record(savedQuote, pending.submittedUnitPrice(), pending.reason(), operatorStaffId, operatorStaffId);
            }
        }

        // 7. 只核销优惠券；净应付更新后，POS自己的收款流程执行一次。
        if (command.getCouponId() != null) {
            couponAmount = paymentService.applyCouponToOrder(order, order.getCustomerId(), command.getCouponId());
        }

        // 8. 重新计算实付金额并更新订单
        BigDecimal payAmount = totalAmount.subtract(discountAmount).subtract(couponAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_AMOUNT_INVALID + ": 实付金额不能小于0");
        }
        
        // 净应结算包含赊销，不能清零。纯赊销只挂优惠后实际净额。
        BigDecimal creditAmount = command.getPaymentType().equals(OrderMainPaymentMethod.CREDIT.getCode()) ? payAmount : BigDecimal.ZERO;
        if (creditAmount.signum()>0 && order.getCustomerId()==null) throw new IllegalArgumentException("赊销必须指定客户");

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
                consumePosWalletAuthorization(order, command, balanceUsed);

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

        } else if (command.getPaymentType() != OrderMainPaymentMethod.BALANCE.getCode()) {
            savePaymentRecords(order, command, createdBy);
        }

        // 10. 处理内部支付扣减（只在未由 payments 明细处理时执行）
        if (!paymentsProvided && command.getPaymentType().equals(OrderMainPaymentMethod.BALANCE.getCode())) {
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

            consumePosWalletAuthorization(order, command, balanceAmount);

            // 执行扣减：专用金优先，随后本金
            if (specialFundAmount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.deductSpecialFund(order.getCustomerId(), specialFundAmount,
                    "ORDER", order.getId(), createdBy, "POS订单支付");
                recordPosPayment(order.getId(), PaymentMethod.SPECIAL_FUND.getCode(), specialFundAmount);
            }
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                logger.info("About to deduct balance for customerId={} orderId={} amount={}", order.getCustomerId(), order.getId(), balanceAmount);
                walletService.deductBalance(order.getCustomerId(), balanceAmount,
                    "ORDER", order.getId(), createdBy, "POS订单支付");
                recordPosPayment(order.getId(), PaymentMethod.BALANCE.getCode(), balanceAmount);
                logger.info("Deducted balance for customerId={} orderId={} amount={}", order.getCustomerId(), order.getId(), balanceAmount);
            }
        }
        
        // 12. ⛔️ 赊销记账（Critical Fix）
        // 使用前面保留的 creditAmount（订单总额-折扣-优惠券），而非 order.getPayAmount()（赊销订单已清零为0）
        if (!paymentsProvided && command.getPaymentType().equals(OrderMainPaymentMethod.CREDIT.getCode()) && creditAmount.signum()>0) {
            creditService.addDebt(order.getCustomerId(), order.getId(), creditAmount);
        }


        // 11. 扣减库存（POS订单立即扣减，使用操作员所属门店ID）
        // 重要：订单已保存，更新所有库存扣减命令的 referenceId
        List<Integer> stockIndices = java.util.stream.IntStream.range(0, inventoryOutCommands.size()).boxed()
                .sorted(java.util.Comparator.comparing(i -> inventoryOutCommands.get(i).getProductId())).toList();
        for (int i : stockIndices) {
            InventoryOutCommand outCommand = inventoryOutCommands.get(i);
            outCommand.setReferenceId(order.getId());
            outCommand.setReferenceLineId(items.get(i).getId());
            inventoryService.outbound(outCommand, createdBy);
            if (deliveryType == 1 && Boolean.TRUE.equals(pricedInputs.get(i).getWarehousePickup())) {
                pickupFulfillmentService.createRequest(order.getId(), items.get(i).getId(), null);
            }
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
    
    /**
     * 分页查询订单列表
     * 
     * @param query 查询条件
     * @return 分页结果
     */
    public com.mybatisflex.core.paginate.Page<OrderPageVO> queryOrderPage(OrderPageQuery query) {
        // 1. 查询订单分页数据
        com.mybatisflex.core.paginate.Page<Order> page = query.getDeliveryStatus() == null
                ? orderRepository.findPage(query.getPageNum(), query.getPageSize(), query.getKeyword(), query.getStoreId(),
                    query.getOrderType(), query.getDeliveryType(), query.getOrderStatus(), query.getStartTime(), query.getEndTime(), query.getCustomerId())
                : orderRepository.findPage(query.getPageNum(), query.getPageSize(), query.getKeyword(), query.getStoreId(),
                    query.getOrderType(), query.getDeliveryType(), query.getOrderStatus(), query.getStartTime(), query.getEndTime(), query.getCustomerId(), query.getDeliveryStatus());

        List<Long> fullyRefundedIds=page.getRecords().stream().filter(order -> Integer.valueOf(8).equals(order.getOrderStatus()))
                .map(Order::getId).toList();
        java.util.Set<Long> returnableIds=fullyRefundedIds.isEmpty()?java.util.Set.of():itemRepository.findOrderIdsWithReturnableItems(fullyRefundedIds);

        // 只复用本次分页内的显示资料（包含查无结果），不跨请求缓存或改变仓储过滤。
        Map<Long, Optional<Store>> pageStores = new HashMap<>();
        Map<Long, Optional<Customer>> pageCustomers = new HashMap<>();

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
            OrderDelivery delivery = deliveryRepository.findByOrderId(order.getId()).orElse(null);
            vo.setDeliveryType(delivery != null ? resolveDeliveryType(delivery)
                    : (java.util.Objects.equals(order.getOrderType(), 2) ? 1 : null));
            vo.setDeliveryStatus(delivery != null ? delivery.getDeliveryStatus() : null);
            vo.setCanShip(canDeliver(order, delivery, true));
            vo.setCanConfirmReceipt(canDeliver(order, delivery, false));
            vo.setOrderStatus(order.getOrderStatus());
            if (Integer.valueOf(8).equals(order.getOrderStatus())) vo.setHasReturnableItems(returnableIds.contains(order.getId()));
            vo.setTotalAmount(order.getTotalAmount());
            vo.setDiscountAmount(order.getDiscountAmount());
            vo.setCouponAmount(order.getCouponAmount());
            vo.setPayAmount(order.getPayAmount());
            vo.setReceivedAmount(paymentSummary.receivedAmount);
            vo.setCreditAmount(paymentSummary.creditAmount);
        var finance=paymentService.financialSummary(order);
        vo.setCreditReductionAmount(finance.creditReductionAmount());vo.setCashRefundedAmount(finance.cashRefundedAmount());
        vo.setRemainingCreditAmount(finance.remainingCreditAmount());vo.setRepaidAmount(finance.repaidAmount());
            vo.setReceiverName(order.getReceiverName());
            vo.setReceiverPhone(order.getReceiverPhone());
            vo.setReceiverAddress(order.getReceiverAddress());
            vo.setPickupCode(order.getPickupCode());
            vo.setCreatedAt(order.getCreatedAt());
            
            // 查询门店名称
            if (order.getStoreId() != null) {
                pageStores.computeIfAbsent(order.getStoreId(), storeRepository::findByIdIncludingDeleted).ifPresent(store -> {
                    vo.setStoreName(store.getName());
                });
            }
            
            // 查询客户信息
            if (order.getCustomerId() != null) {
                pageCustomers.computeIfAbsent(order.getCustomerId(), customerRepository::findByIdIncludingDeleted).ifPresent(customer -> {
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
        var finance=paymentService.financialSummary(order);
        vo.setCreditReductionAmount(finance.creditReductionAmount());vo.setCashRefundedAmount(finance.cashRefundedAmount());
        vo.setRemainingCreditAmount(finance.remainingCreditAmount());vo.setRepaidAmount(finance.repaidAmount());
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaymentMethodName(resolveMainPaymentMethodName(order.getPaymentMethod()));
        vo.setPickupCode(order.getPickupCode());
        vo.setCreatedAt(order.getCreatedAt());
        
        // 转换明细
        Map<Long, BigDecimal> refundBases = RefundAmountPolicy.refundableBaseAmounts(order, items);
        vo.setItems(items.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            itemVO.setId(item.getId());
            itemVO.setProductId(item.getProductId());
            itemVO.setPackageId(item.getPackageId());
            itemVO.setPackageGroupNo(item.getPackageGroupNo());
            itemVO.setPackageName(item.getPackageName());
            itemVO.setPackageQuantity(item.getPackageQuantity());
            itemVO.setPackageUnitPrice(item.getPackageUnitPrice());
            itemVO.setItemType(item.getItemType());
            itemVO.setProductName(item.getProductName());
            itemVO.setQuantity(item.getQuantity());
            itemVO.setUnit(item.getUnit());
            enrichQuantityMetadata(itemVO, item);
            itemVO.setUnitPrice(item.getUnitPrice());
            itemVO.setTotalPrice(item.getTotalPrice());
            RefundAggregate refundAggregate = refundAggregates.get(item.getId());
            BigDecimal refundedQuantity = refundAggregate != null ? refundAggregate.quantity : BigDecimal.ZERO;
            BigDecimal refundedAmount = refundAggregate != null ? refundAggregate.amount : BigDecimal.ZERO;
            itemVO.setRefundedQuantity(refundedQuantity);
            itemVO.setRefundedAmount(refundedAmount);
            itemVO.setRefundableQuantity(safeSubtract(item.getQuantity(), refundedQuantity));
            itemVO.setRefundableAmount(safeSubtract(refundBases.getOrDefault(item.getId(), item.getTotalPrice()), refundedAmount));
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
            deliveryVO.setReceivedAt(delivery.getReceivedAt());
            vo.setCanShip(canDeliver(order, delivery, true));
            vo.setCanConfirmReceipt(canDeliver(order, delivery, false));
            if (vo.isCanConfirmReceipt()) {
                var deliveryItems = orderRepository.findRemainingDeliveryItems(orderId);
                vo.setDeliveryItems(deliveryItems);
                vo.setDeliveryConfirmationKey(deliveryConfirmationKey(order, delivery, deliveryItems));
            }
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
        // 操作员仅按原POS员工事实解析；线上createdBy是客户编号。
        vo.setRemark(order.getRemark());
        Long operatorId = Integer.valueOf(1).equals(order.getOrderType())
                ? (order.getStaffId() != null ? order.getStaffId() : order.getCreatedBy()) : null;
        vo.setOperatorStaffId(operatorId);
        if (operatorId != null) {
            try {
                staffRepository.findById(operatorId).ifPresent(staff -> vo.setOperatorName(staff.getName()));
            } catch (Exception ignored) {
                // 姓名补充失败不改变原操作员工编号。
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
                afterSalesVO.setCreditReductionAmount(afterSales.getCreditReductionAmount());afterSalesVO.setCashRefundAmount(afterSales.getCashRefundAmount());
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
        return loadOrderItemRefundAggregates(orderId,null);
    }
    private java.util.Map<Long, RefundAggregate> loadOrderItemRefundAggregates(Long orderId,Long excludedAfterSalesId) {
        java.util.Map<Long, RefundAggregate> aggregates = new java.util.HashMap<>();
        List<com.agri_system.domain.after_sales.model.AfterSales> afterSalesList = afterSalesRepository.findByOrderId(orderId);
        if (afterSalesList == null || afterSalesList.isEmpty()) {
            return aggregates;
        }

        for (com.agri_system.domain.after_sales.model.AfterSales afterSales : afterSalesList) {
            if (afterSales == null || java.util.Objects.equals(afterSales.getId(),excludedAfterSalesId)
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

    private void enrichQuantityMetadata(OrderItemVO vo, OrderItem item) {
        var metadata = inventoryService.returnQuantityMetadata(item);
        if (metadata == null) return;
        vo.setBaseUnitName(metadata.baseUnitName());
        vo.setAllowFractionalBaseQuantity(metadata.allowFractionalBaseQuantity());
        vo.setOriginalToBaseRatio(metadata.originalToBaseRatio());
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

    public record ReassignmentPreview(String storeId,String orderId,String currentStaffId,String version,boolean canReassign,String blockReason) {}

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
        return afterSalesRepository.findByOrderId(orderId).stream()
                .anyMatch(fact -> java.util.Objects.equals(fact.getStatus(), 10) || java.util.Objects.equals(fact.getStatus(), 40));
    }

    private String deliveryConfirmationKey(Order order, OrderDelivery delivery,
            List<com.agri_system.domain.order.model.DeliveryItemQuantity> items) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item.getOrderItemId() == null
                || item.getQuantity() == null || item.getQuantity().signum() <= 0 || !StringUtils.hasText(item.getUnit()))) return null;
        // 仅绑定本次所见内容，不是新业务身份或存储来源；签收时在原订单锁内重算。
        var parts = new ArrayList<String>();
        parts.add(String.valueOf(order.getId())); parts.add(String.valueOf(order.getOrderStatus()));
        parts.add(String.valueOf(delivery.getDeliveryType())); parts.add(String.valueOf(delivery.getDeliveryStatus()));
        parts.add(String.valueOf(delivery.getDeliveryStaffId()));
        parts.add(String.valueOf(delivery.getAssignmentVersion()));
        parts.add(String.valueOf(order.getReceiverName())); parts.add(String.valueOf(order.getReceiverPhone()));
        parts.add(String.valueOf(order.getReceiverAddress()));
        items.stream().sorted(java.util.Comparator.comparing(com.agri_system.domain.order.model.DeliveryItemQuantity::getOrderItemId)
                .thenComparing(com.agri_system.domain.order.model.DeliveryItemQuantity::getUnit)).forEach(item -> {
            parts.add(String.valueOf(item.getOrderItemId())); parts.add(item.getProductName());
            parts.add(item.getQuantity().stripTrailingZeros().toPlainString()); parts.add(item.getUnit());
        });
        // 长度前缀防止商品名或地址里的分隔符造成相同摘要。
        return refundHash(parts.stream().map(value -> value == null ? "-1:" : value.length() + ":" + value)
                .collect(Collectors.joining()));
    }

    private boolean canDeliver(Order order, OrderDelivery delivery, boolean shipping) {
        if (delivery == null || !java.util.Objects.equals(delivery.getDeliveryStatus(), shipping ? 2 : 3)) return false;
        Integer type = resolveDeliveryType(delivery);
        if (!Integer.valueOf(2).equals(type) && !Integer.valueOf(3).equals(type)) return false;
        Integer status = order.getOrderStatus();
        return (java.util.Objects.equals(status, shipping ? 4 : 5) || java.util.Objects.equals(status, 9))
                && !hasProcessingDeliveryAfterSales(order.getId())
                && orderRepository.hasRemainingDeliveryQuantity(order.getId());
    }

    private void validateDeliveryAction(Order order, OrderDelivery delivery, boolean shipping) {
        if (!canDeliver(order, delivery, shipping)) {
            throw new IllegalArgumentException("订单当前不能交付：请核对配送状态、处理中售后及剩余实出库物料");
        }
    }

    private int validatePosDelivery(POSOrderCreateCommand command) {
        int type = command.getDeliveryType() == null ? 1 : command.getDeliveryType();
        if (type != 1 && type != 2) throw new IllegalArgumentException("POS履约方式只支持当场交货或门店配送");
        if (type == 2 && (!StringUtils.hasText(command.getReceiverName()) || !StringUtils.hasText(command.getReceiverPhone())
                || !StringUtils.hasText(command.getReceiverAddress()))) {
            throw new IllegalArgumentException("门店配送必须填写收货人、收货电话及收货地址");
        }
        return type;
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

        // 优惠券已由统一核销入口记录；钱包由实际扣减的同一金额记录流水。
        // 外部支付（现金、微信、支付宝）
        // 只有在非余额支付且非混合支付时才记录外部支付（混合支付不应保存一条总额为混合的记录）
        if (command.getPaymentType() != OrderMainPaymentMethod.BALANCE.getCode()
                && command.getPaymentType() != OrderMainPaymentMethod.MIXED.getCode()) {
            BigDecimal externalAmount = order.getPayAmount();
            // payAmount 已经扣除优惠券，不能再次减券。
            
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

    private void recordPosPayment(Long orderId, int type, BigDecimal amount) {
        OrderPayment payment = new OrderPayment();
        payment.setOrderId(orderId); payment.setPaymentType(type); payment.setAmount(amount);
        payment.setCreatedAt(LocalDateTime.now()); paymentRepository.save(payment);
    }

    /** 单一及混合钱包共用授权；条件更新同时校验客户、金额、有效期和未使用状态。 */
    private void consumePosWalletAuthorization(Order order, POSOrderCreateCommand command, BigDecimal principalAmount) {
        if (principalAmount.signum() <= 0) return;
        String code = command.getWalletAuthCode();
        java.util.Set<String> detailCodes = command.getPayments() == null ? java.util.Set.of()
                : command.getPayments().stream().filter(p -> p.getPaymentType() == OrderMainPaymentMethod.BALANCE.getCode())
                .map(PaymentDetailDTO::getAuthCode).filter(StringUtils::hasText).collect(Collectors.toSet());
        if (detailCodes.size() > 1 || (StringUtils.hasText(code) && detailCodes.stream().anyMatch(value -> !value.equals(command.getWalletAuthCode())))) {
            throw new IllegalArgumentException("同一次钱包支付只能使用同一个验证码");
        }
        if (!StringUtils.hasText(code) && !detailCodes.isEmpty()) code = detailCodes.iterator().next();
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException(OrderErrorCode.WALLET_AUTH_CODE_INVALID + ": 钱包验证码不能为空");
        }
        int updated = walletAuthCodeRepository.consumeAuthCode(order.getCustomerId(), code,
                principalAmount.setScale(2, RoundingMode.UNNECESSARY), order.getId());
        if (updated == 0) throw new IllegalStateException("验证码核销失败：验证码无效、已过期、金额不匹配或已被使用");
    }

    /** 下单只允许真实收款方式，优惠券及专用金拆分由后端产生。 */
    private void validatePosPayments(POSOrderCreateCommand command) {
        java.util.Set<Integer> mainTypes = java.util.Set.of(1, 2, 3, 4, 6, 10);
        if (command.getPaymentType() == null || !mainTypes.contains(command.getPaymentType())) {
            throw new IllegalArgumentException("POS支付方式不正确");
        }
        if (command.getPaymentType() == OrderMainPaymentMethod.BALANCE.getCode() && command.getCustomerId() == null) {
            throw new IllegalArgumentException("余额支付必须指定客户");
        }
        if (command.getPayments() == null || command.getPayments().isEmpty()) {
            if (command.getPaymentType() == OrderMainPaymentMethod.MIXED.getCode()) {
                throw new IllegalArgumentException("混合支付必须提供支付明细");
            }
            return;
        }
        java.util.Set<Integer> tenderTypes = java.util.Set.of(1, 2, 3, 4, 6);
        for (PaymentDetailDTO detail : command.getPayments()) {
            if (detail == null || detail.getPaymentType() == null || !tenderTypes.contains(detail.getPaymentType())) {
                throw new IllegalArgumentException("支付明细方式不正确");
            }
            if (detail.getAmount() == null || detail.getAmount().signum() <= 0
                    || detail.getAmount().stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("每笔支付明细金额必须大于0且精确到分");
            }
        }
    }

    private record PackageLineContext(int groupNo,
            com.agri_system.application.productpackage.dto.vo.PackagePriceQuote quote,
            com.agri_system.application.productpackage.dto.vo.PackagePriceQuote.Item material) {}

    /** 前端只提交套餐与套数，物料组成与成交金额由当前后端报价生成。 */
    private List<OrderItemDTO> expandPackageSales(List<OrderItemDTO> commands, Long storeId, Long customerId,
                                                 Long operatorId, Map<OrderItemDTO, PackageLineContext> contexts) {
        // 锁序与购物车展示顺序分离，防止两笔多套餐销售按A/B、B/A互相等待。
        Map<OrderItemDTO, com.agri_system.application.productpackage.dto.vo.PackagePriceQuote> packageQuotes = new java.util.IdentityHashMap<>();
        for (OrderItemDTO command : commands.stream().filter(row -> Integer.valueOf(2).equals(row.getItemType()))
                .sorted(java.util.Comparator.comparing(OrderItemDTO::getPackageId, java.util.Comparator.nullsFirst(Long::compareTo))).toList()) {
            if (command.getPackageId() == null || command.getProductId() != null) throw new IllegalArgumentException("套餐行只能指定套餐ID");
            if (!StringUtils.hasText(command.getPackageQuoteId())) throw new IllegalArgumentException("套餐请先获取有效报价");
            packageQuotes.put(command, packagePricingService.quoteForSale(command.getPackageId(), customerId, storeId,
                    command.getQuantity(), command.getUnitPrice()));
        }
        List<OrderItemDTO> expanded = new ArrayList<>();
        int groupNo = 0;
        for (OrderItemDTO command : commands) {
            groupNo++;
            if (!Integer.valueOf(2).equals(command.getItemType())) {
                if (command.getPackageId() != null) throw new IllegalArgumentException("套餐必须整套提交，不能上传前端拆分商品");
                expanded.add(command); continue;
            }
            var quote = packageQuotes.get(command);
            if (!command.getPackageQuoteId().equals(quote.quoteId())
                    || (command.getQuotedUnitPrice() != null && command.getQuotedUnitPrice().compareTo(quote.suggestedUnitPrice()) != 0)) {
                throw new IllegalArgumentException("套餐价格或组成已变化，请重新报价后结算");
            }
            boolean override = quote.dealUnitPrice().compareTo(quote.suggestedUnitPrice()) != 0 || quote.belowReference();
            if (override) {
                var first = quote.items().get(0);
                var packageQuote = new PriceQuote(quote.quoteId(), null, first.productId(), storeId, "POS", null,
                        null, "套", null, LocalDate.now(), quote.suggestedUnitPrice(), "PACKAGE", quote.referenceFloor(),
                        quote.belowReference(), false);
                manualPriceAuditService.validateChange(packageQuote, quote.dealUnitPrice(), command.getPriceChangeReason(), operatorId);
            }
            for (var material : quote.items()) {
                OrderItemDTO line = new OrderItemDTO();
                line.setProductId(material.productId()); line.setPackageId(command.getPackageId()); line.setItemType(1);
                line.setQuantity(material.quantity()); line.setUnit(material.unit()); line.setUnitPrice(material.unitPrice());
                line.setWarehousePickup(command.getWarehousePickup()); line.setPriceChangeReason(command.getPriceChangeReason());
                contexts.put(line, new PackageLineContext(groupNo, quote, material)); expanded.add(line);
            }
        }
        return expanded;
    }

    /** 明细保存拿到 ID 后追加议价记录，成交价留在订单行，不随商品改价变化。 */
    private record PendingPriceChange(OrderItem item, PriceQuote quote, BigDecimal submittedUnitPrice, String reason, boolean packageAllocation) {
    }

}
