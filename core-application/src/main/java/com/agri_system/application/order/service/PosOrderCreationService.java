package com.agri_system.application.order.service;
import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.vo.*;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.service.ProductPricingService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.wallet.repository.WalletAuthCodeRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** POS成交完整事务编排；原请求协调器仍先锁持久请求，再进入本服务的钱货事务。 */
@Service
@RequiredArgsConstructor
public class PosOrderCreationService {
    private static final Logger logger=LoggerFactory.getLogger(PosOrderCreationService.class);
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
    private final ProductPricingService productPricingService;
    private final com.agri_system.application.productpackage.service.PackagePricingService packagePricingService;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletAuthCodeRepository walletAuthCodeRepository;
    private final PickupFulfillmentService pickupFulfillmentService;
    private final ManualPriceAuditService manualPriceAuditService;

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

    private int validatePosDelivery(POSOrderCreateCommand command) {
        return PosOrderInputPolicy.validatePosDelivery(command);
    }

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
        PosOrderInputPolicy.validatePosPayments(command);
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
