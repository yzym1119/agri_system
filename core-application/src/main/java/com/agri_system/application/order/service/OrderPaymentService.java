package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.application.order.dto.vo.OrderPaymentVO;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.OrderPayment;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.order.repository.OrderPaymentRepository;
import com.agri_system.domain.order.service.OrderDomainService;
import com.agri_system.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 订单支付服务
 * 实现混合支付扣减链和退款链
 * 
 * @author AI Generated
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {
    private final OrderPaymentRepository paymentRepository;
    private final CustomerCouponRepository couponRepository;
    private final WalletApplicationService walletService;
    private final OrderDomainService orderDomainService;
    private final OrderItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final com.agri_system.domain.order.repository.OrderRepository orderRepository;
    private final com.agri_system.domain.customer.repository.CustomerRepository customerRepository;
    private final com.agri_system.domain.credit.repository.CreditAccountRepository creditAccountRepository;
    private final com.agri_system.domain.credit.repository.CreditRepaymentLogRepository repaymentRepository;
    private final com.agri_system.domain.after_sales.repository.AfterSalesRepository afterSalesRepository;
    private final com.agri_system.domain.after_sales.repository.AfterSalesItemRepository afterSalesItemRepository;
    
    /**
     * 预运算支付金额（不实际扣减），外部支付渠道按默认（微信）展示。
     */
    public OrderPayPreviewVO previewPayment(Order order, Boolean useBalance, Boolean useSpecialFund, Long couponId) {
        return previewPayment(order, useBalance, useSpecialFund, couponId, null);
    }

    /**
     * 预运算支付金额（不实际扣减）
     *
     * @param order 订单
     * @param useBalance 是否使用本金余额
     * @param useSpecialFund 是否使用专用金
     * @param couponId 优惠券ID
     * @param externalPaymentType 外部支付方式：2-微信，3-支付宝；为空按微信
     * @return 支付预览
     */
    public OrderPayPreviewVO previewPayment(Order order, Boolean useBalance, Boolean useSpecialFund, Long couponId,
                                            Integer externalPaymentType) {
        BigDecimal totalAmount = order.getPayAmount();
        List<OrderPaymentVO> payments = new ArrayList<>();
        
        BigDecimal remaining = totalAmount;
        BigDecimal couponAmount = BigDecimal.ZERO;
        BigDecimal specialFundAmount = BigDecimal.ZERO;
        BigDecimal balanceAmount = BigDecimal.ZERO;
        
        // 1. 优惠券抵扣
        if (couponId != null && order.getCustomerId() != null) {
            CustomerCoupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券不存在"));
            validateCouponOwnership(order, coupon);
            
            // 获取订单商品ID和分类ID用于校验
            List<OrderItem> items = itemRepository.findByOrderId(order.getId());
            List<Long> productIds = items.stream()
                    .filter(item -> item.getProductId() != null)
                    .map(OrderItem::getProductId)
                    .collect(Collectors.toList());
            List<Long> categoryIds = resolveCategoryIds(productIds);
            
            if (coupon.isUsable(totalAmount, productIds, categoryIds)) {
                // 优惠券一次核销：实际抵扣 = min(订单待付, 券面额或折扣计算值)
                BigDecimal faceValue = coupon.getAvailableAmount(remaining);
                couponAmount = remaining.min(faceValue);
                remaining = remaining.subtract(couponAmount);
                
                OrderPaymentVO payment = new OrderPaymentVO();
                payment.setPaymentType(PaymentMethod.COUPON.getCode());
                payment.setAmount(couponAmount);
                payment.setCouponId(couponId);
                payments.add(payment);
            }
        }
        
        // 2. 专用金抵扣（仅允许专用金的商品）
        if (Boolean.TRUE.equals(useSpecialFund) && order.getCustomerId() != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
            List<OrderItem> items = itemRepository.findByOrderId(order.getId());
            BigDecimal maxSpecialFund = orderDomainService.calculateMaxSpecialFundAmount(items);
            BigDecimal specialFundBalance = walletService.getSpecialFundBalance(order.getCustomerId());
            
            specialFundAmount = remaining.compareTo(maxSpecialFund) > 0 ? maxSpecialFund : remaining;
            if (specialFundAmount.compareTo(specialFundBalance) > 0) {
                specialFundAmount = specialFundBalance;
            }
            
            if (specialFundAmount.compareTo(BigDecimal.ZERO) > 0) {
                remaining = remaining.subtract(specialFundAmount);
                
                OrderPaymentVO payment = new OrderPaymentVO();
                payment.setPaymentType(PaymentMethod.SPECIAL_FUND.getCode());
                payment.setAmount(specialFundAmount);
                payments.add(payment);
            }
        }
        
        // 3. 本金余额抵扣
        if (Boolean.TRUE.equals(useBalance) && order.getCustomerId() != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balance = walletService.getBalance(order.getCustomerId());
            balanceAmount = remaining.compareTo(balance) > 0 ? balance : remaining;
            
            if (balanceAmount.compareTo(BigDecimal.ZERO) > 0) {
                remaining = remaining.subtract(balanceAmount);
                
                OrderPaymentVO payment = new OrderPaymentVO();
                payment.setPaymentType(PaymentMethod.BALANCE.getCode());
                payment.setAmount(balanceAmount);
                payments.add(payment);
            }
        }
        
        // 4. 外部支付（微信/支付宝）：渠道必须与本次发起/确认一致，不能一律标成微信
        BigDecimal externalAmount = remaining;
        if (externalAmount.compareTo(BigDecimal.ZERO) > 0) {
            OrderPaymentVO payment = new OrderPaymentVO();
            payment.setPaymentType(resolveExternalPaymentType(externalPaymentType));
            payment.setAmount(externalAmount);
            payments.add(payment);
        }
        
        OrderPayPreviewVO preview = new OrderPayPreviewVO();
        preview.setTotalAmount(totalAmount);
        preview.setCouponAmount(couponAmount);
        preview.setSpecialFundAmount(specialFundAmount);
        preview.setBalanceAmount(balanceAmount);
        preview.setExternalAmount(externalAmount);
        preview.setPayments(payments);
        
        return preview;
    }
    
    /**
     * 执行混合支付扣减（实际扣减）
     * 
     * @param order 订单
     * @param useBalance 是否使用本金余额
     * @param useSpecialFund 是否使用专用金
     * @param couponId 优惠券ID
     * @param createdBy 操作人ID
     * @return 支付明细列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderPayment> executePayment(Order order, Boolean useBalance, Boolean useSpecialFund, 
                                            Long couponId, Long createdBy,
                                            boolean includeExternalPayment,
                                            Integer externalPaymentType,
                                            String externalTransactionNo,
                                            LocalDateTime externalPaidAt) {
        // 先预运算：渠道与本次执行一致，预览明细与落库明细同源
        OrderPayPreviewVO preview = previewPayment(order, useBalance, useSpecialFund, couponId, externalPaymentType);

        List<OrderPayment> payments = new ArrayList<>();
        BigDecimal totalPaid = BigDecimal.ZERO;

        // 优惠券只核销一次。POS复用同一规则，但其现金/钱包收款由POS入口负责。
        if (couponId != null) {
            OrderPayment couponPayment = applyCouponPayment(order, order.getCustomerId(), couponId);
            payments.add(couponPayment);
            totalPaid = totalPaid.add(couponPayment.getAmount());
        }

        // 2. 专用金抵扣
        if (preview.getSpecialFundAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductSpecialFund(order.getCustomerId(), preview.getSpecialFundAmount(), 
                                           "ORDER", order.getId(), createdBy, "订单支付扣减专用金");
            
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(PaymentMethod.SPECIAL_FUND.getCode());
            payment.setAmount(preview.getSpecialFundAmount());
            payment.setCreatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            payments.add(payment);
            totalPaid = totalPaid.add(preview.getSpecialFundAmount());
        }
        
        // 3. 本金余额抵扣
        if (preview.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductBalance(order.getCustomerId(), preview.getBalanceAmount(), 
                                       "ORDER", order.getId(), createdBy, "订单支付扣减本金余额");
            
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(PaymentMethod.BALANCE.getCode());
            payment.setAmount(preview.getBalanceAmount());
            payment.setCreatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            payments.add(payment);
            totalPaid = totalPaid.add(preview.getBalanceAmount());
        }
        
        // 4. 外部支付（微信/支付宝）- 仅在已确认外部支付后落库
        if (includeExternalPayment && preview.getExternalAmount().compareTo(BigDecimal.ZERO) > 0) {
            Integer resolvedType = resolveExternalPaymentType(externalPaymentType);
            // 全局唯一性预检：同一平台流水只能归属一笔有效业务收款（V140 唯一索引兜底）。
            // 重复/冲突通知绝不静默入账——留痕后拒绝，进入异常对账流程。
            if (externalTransactionNo != null && paymentRepository
                    .findByPaymentTypeAndOuterTransNo(resolvedType, externalTransactionNo).isPresent()) {
                log.warn("外部支付流水冲突：流水号已被其他收款占用，拒绝入账待人工对账: outerTransNo={}, paymentType={}, orderId={}",
                        externalTransactionNo, resolvedType, order.getId());
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                        + ": 外部流水 " + externalTransactionNo + " 已归属其他收款，禁止重复入账，请人工对账");
            }
            OrderPayment payment = new OrderPayment();
            payment.setOrderId(order.getId());
            payment.setPaymentType(resolvedType);
            payment.setAmount(preview.getExternalAmount());
            payment.setOuterTransNo(externalTransactionNo);
            payment.setCallbackTime(externalPaidAt != null ? externalPaidAt : LocalDateTime.now());
            payment.setCreatedAt(LocalDateTime.now());
            try {
                payment = paymentRepository.save(payment);
            } catch (org.springframework.dao.DuplicateKeyException raceWithConcurrentCallback) {
                // 并发窗口内预检后被抢先：DB 唯一索引兜底，同样拒绝入账进入对账
                log.warn("外部支付流水唯一约束冲突（并发回调），拒绝入账待人工对账: outerTransNo={}, paymentType={}, orderId={}",
                        externalTransactionNo, resolvedType, order.getId());
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                        + ": 外部流水 " + externalTransactionNo + " 并发重复通知，已拒绝入账，请人工对账");
            }
            payments.add(payment);
            totalPaid = totalPaid.add(preview.getExternalAmount());
        }
        
        BigDecimal expectedPaid = includeExternalPayment
                ? order.getPayAmount()
                : order.getPayAmount().subtract(preview.getExternalAmount());
        if (totalPaid.compareTo(expectedPaid) != 0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 支付总额不匹配");
        }
        
        // 优惠券是成交优惠，不是实收。完成收款后才固化原净应结算额，预览不能改订单。
        BigDecimal usedCoupon=payments.stream().filter(p->Integer.valueOf(PaymentMethod.COUPON.getCode()).equals(p.getPaymentType()))
                .map(OrderPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal netAmount=order.getPayAmount().subtract(usedCoupon);
        BigDecimal received=payments.stream().filter(p->!Integer.valueOf(PaymentMethod.COUPON.getCode()).equals(p.getPaymentType()))
                .map(OrderPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(netAmount.signum()<0 || received.compareTo(netAmount)!=0) {
            throw new IllegalArgumentException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH + ": 尚未完成订单原净额收款");
        }
        order.setCouponAmount((order.getCouponAmount()==null?BigDecimal.ZERO:order.getCouponAmount()).add(usedCoupon).setScale(2));
        order.setPayAmount(netAmount.setScale(2));
        order.setPaymentMethod(completedMainPaymentMethod(payments));
        orderRepository.save(order);
        return payments;
    }

    private int completedMainPaymentMethod(List<OrderPayment> payments) {
        var methods=payments.stream().filter(p->p.getAmount().signum()>0 && !Integer.valueOf(PaymentMethod.COUPON.getCode()).equals(p.getPaymentType()))
                .map(p->Integer.valueOf(PaymentMethod.SPECIAL_FUND.getCode()).equals(p.getPaymentType())
                        ? OrderMainPaymentMethod.BALANCE.getCode():p.getPaymentType()).distinct().toList();
        if(methods.isEmpty()) return OrderMainPaymentMethod.COUPON.getCode();
        if(methods.size()>1) return OrderMainPaymentMethod.MIXED.getCode();
        return methods.get(0);
    }

    /** 仅核销优惠券并记录抵扣，不扣钱包、不生成其它收款流水。 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal applyCouponToOrder(Order order, Long customerId, Long couponId) {
        if (couponId == null) return BigDecimal.ZERO;
        return applyCouponPayment(order, customerId, couponId).getAmount();
    }

    private OrderPayment applyCouponPayment(Order order, Long customerId, Long couponId) {
        if (customerId == null || !customerId.equals(order.getCustomerId())) {
            throw new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 使用优惠券必须指定订单客户");
        }
        CustomerCoupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券不存在"));
        validateCouponOwnership(order, coupon);
        List<Long> productIds = itemRepository.findByOrderId(order.getId()).stream()
                .map(OrderItem::getProductId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!coupon.isUsable(order.getPayAmount(), productIds, resolveCategoryIds(productIds))) {
            throw new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券已失效或不满足使用条件");
        }
        BigDecimal amount = coupon.getAvailableAmount(order.getPayAmount()).min(order.getPayAmount());
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券抵扣金额无效");
        }
        amount = coupon.use(order.getId(), order.getPayAmount());
        couponRepository.save(coupon);
        OrderPayment payment = new OrderPayment();
        payment.setOrderId(order.getId()); payment.setPaymentType(PaymentMethod.COUPON.getCode());
        payment.setAmount(amount); payment.setCouponId(couponId); payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    private void validateCouponOwnership(Order order, CustomerCoupon coupon) {
        if (coupon.getCustomerId() == null || !coupon.getCustomerId().equals(order.getCustomerId())) {
            throw new IllegalArgumentException(OrderErrorCode.COUPON_INVALID + ": 优惠券不属于当前客户");
        }
    }

    private List<Long> resolveCategoryIds(List<Long> productIds) {
        return productIds.stream()
                .map(productRepository::findById)
                .flatMap(Optional::stream)
                .map(product -> product.getCategoryId())
                .filter(categoryId -> categoryId != null)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 外部支付渠道唯一解析入口：为空按微信，只接受 2-微信 / 3-支付宝。 */
    static Integer resolveExternalPaymentType(Integer externalPaymentType) {
        if (externalPaymentType == null) {
            return OrderMainPaymentMethod.WECHAT.getCode();
        }
        if (!externalPaymentType.equals(OrderMainPaymentMethod.WECHAT.getCode())
                && !externalPaymentType.equals(OrderMainPaymentMethod.ALIPAY.getCode())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 外部支付方式不支持");
        }
        return externalPaymentType;
    }
    
    public record RefundResult(BigDecimal cashRefundAmount, BigDecimal creditReductionAmount) {}
    public record RefundSourceView(String sourceType, Long sourceId, String paymentMethodName,
                                   BigDecimal amount, BigDecimal refundableAmount) {}
    public record RefundFundingPreview(BigDecimal refundAmount, BigDecimal creditReductionAmount,
                                       BigDecimal cashRefundAmount, BigDecimal remainingDebt,
                                       BigDecimal remainingRefundableAmount, boolean creditOrder,
                                       String state, List<RefundSourceView> sources) {}
    public record FinancialSummary(BigDecimal creditReductionAmount, BigDecimal cashRefundedAmount,
                                   BigDecimal remainingCreditAmount, BigDecimal repaidAmount) {}
    private record Source(OrderPayment payment, com.agri_system.domain.credit.model.CreditRepaymentLog repayment,
                          String type, Long id, String methodName, BigDecimal amount, BigDecimal refunded,
                          LocalDateTime createdAt) {
        BigDecimal available() { return amount.subtract(refunded); }
    }
    private record Funding(Order order, com.agri_system.domain.credit.model.CreditAccount account,
                           List<OrderPayment> payments, List<Source> sources, BigDecimal debt,
                           BigDecimal alreadyReturned, String state) {}

    /** 同一锁序用于预览和执行；不会从未知历史来源猜测可退金额。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RefundFundingPreview previewRefund(Order order, BigDecimal returnAmount) {
        return preview(loadFunding(order), returnAmount);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RefundResult refund(Order order, BigDecimal returnAmount, Long createdBy) {
        Funding funding = loadFunding(order);
        RefundFundingPreview plan = preview(funding, returnAmount);
        // 所有来源及恒等式在任何资金变化前已核对。
        if (plan.creditReductionAmount().signum() > 0) {
            var account = funding.account();
            account.setRemainingAmount(account.getRemainingAmount().subtract(plan.creditReductionAmount()));
            account.refreshSettlementStatus();
            creditAccountRepository.save(account);
        }
        for (RefundSourceView selected : plan.sources()) {
            Source source = funding.sources().stream().filter(item -> item.type().equals(selected.sourceType())
                    && item.id().equals(selected.sourceId())).findFirst().orElseThrow();
            BigDecimal amount = selected.amount();
            if (source.payment() != null) {
                int type = source.payment().getPaymentType();
                if (type == PaymentMethod.BALANCE.getCode()) {
                    walletService.refundBalance(order.getCustomerId(), amount, "ORDER_REFUND", order.getId(), createdBy);
                } else if (type == PaymentMethod.SPECIAL_FUND.getCode()) {
                    walletService.refundSpecialFund(order.getCustomerId(), amount, "ORDER_REFUND", order.getId(), createdBy);
                }
                source.payment().setRefundedAmount(source.refunded().add(amount));
                paymentRepository.save(source.payment());
            } else {
                // 还款4是银行转账，不是本金钱包；实际退已收以原来源累计计数记账。
                source.repayment().setRefundedAmount(source.refunded().add(amount));
                repaymentRepository.save(source.repayment());
            }
        }
        return new RefundResult(plan.cashRefundAmount(), plan.creditReductionAmount());
    }

    private RefundFundingPreview preview(Funding funding, BigDecimal requested) {
        BigDecimal remaining = funding.order().getPayAmount().subtract(funding.alreadyReturned());
        BigDecimal amount = requested == null ? remaining : money(requested, "成交冲销金额");
        if (amount.compareTo(remaining) > 0) throw invalid("累计成交冲销不能超过原净应结算额");
        BigDecimal writeOff = amount.min(funding.debt());
        BigDecimal cash = amount.subtract(writeOff), left = cash;
        List<RefundSourceView> chosen = new ArrayList<>();
        for (Source source : funding.sources()) {
            if (left.signum() == 0) break;
            BigDecimal take = source.available().min(left);
            if (take.signum() > 0) chosen.add(new RefundSourceView(source.type(), source.id(), source.methodName(), take, source.available()));
            left = left.subtract(take);
        }
        if (left.signum() != 0) throw invalid("原单可退已收款来源不足，请核对收款及还款分配");
        return new RefundFundingPreview(amount,writeOff,cash,funding.debt(),remaining,
                funding.account() != null,funding.state(),List.copyOf(chosen));
    }

    private Funding loadFunding(Order requested) {
        Order order = orderRepository.findByIdForUpdate(requested.getId())
                .orElseThrow(() -> invalid("订单不存在"));
        BigDecimal net = money(order.getPayAmount(), "原净应结算额");
        if (order.getCustomerId() != null) customerRepository.findByIdWithLock(order.getCustomerId())
                .orElseThrow(() -> invalid("原订单客户不存在，须核对历史账款"));
        var account = creditAccountRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        List<OrderPayment> payments = paymentRepository.findByOrderIdForUpdate(order.getId());
        List<com.agri_system.domain.credit.model.CreditRepaymentLog> repayments = account == null ? List.of()
                : repaymentRepository.findByCreditAccountIdForUpdate(account.getId());
        BigDecimal returned = BigDecimal.ZERO, writtenOff = BigDecimal.ZERO, cashReturned = BigDecimal.ZERO;
        StringBuilder state = new StringBuilder().append(order.getId()).append('|').append(net);
        List<com.agri_system.domain.after_sales.model.AfterSales> history = afterSalesRepository.findByOrderId(order.getId());
        history = history.stream().sorted(java.util.Comparator.comparing(com.agri_system.domain.after_sales.model.AfterSales::getId)).toList();
        for (var fact : history) {
            if (!Integer.valueOf(com.agri_system.domain.after_sales.model.AfterSales.STATUS_FINISHED).equals(fact.getStatus())) continue;
            BigDecimal r = money(fact.getRefundAmount(), "历史成交冲销"), w = money(fact.getCreditReductionAmount(), "历史冲欠款"), f = money(fact.getCashRefundAmount(), "历史退已收款");
            if (r.compareTo(w.add(f)) != 0) throw invalid("历史售后资金拆分不守恒");
            returned=returned.add(r); writtenOff=writtenOff.add(w);cashReturned=cashReturned.add(f);
            state.append("|AS:").append(fact.getId()).append(':').append(r).append(':').append(w).append(':').append(f);
        }
        BigDecimal originalCash = BigDecimal.ZERO, originalCredit = BigDecimal.ZERO, sourceRefunded = BigDecimal.ZERO;
        List<Source> sources = new ArrayList<>();
        for (OrderPayment payment : payments) {
            if (!java.util.Objects.equals(payment.getOrderId(),order.getId()) || payment.getId()==null) throw invalid("收款来源缺少原单归属");
            BigDecimal amount = money(payment.getAmount(), "原支付金额");
            Integer type=payment.getPaymentType();
            if (type == null) throw invalid("原支付方式未知，须核对");
            if (type == PaymentMethod.COUPON.getCode()) continue;
            if (type == PaymentMethod.CREDIT.getCode()) {originalCredit=originalCredit.add(amount);continue;}
            String name = paymentName(type,false);
            BigDecimal refunded=counter(payment.getRefundedAmount(),amount);
            originalCash=originalCash.add(amount);sourceRefunded=sourceRefunded.add(refunded);
            sources.add(new Source(payment,null,"ORDER_PAYMENT",payment.getId(),name,amount,refunded,payment.getCreatedAt()));
        }
        BigDecimal paid=BigDecimal.ZERO,debt=BigDecimal.ZERO;
        if (account != null) {
            if (!java.util.Objects.equals(account.getOrderId(),order.getId()) || !java.util.Objects.equals(account.getCustomerId(),order.getCustomerId())
                    || !java.util.Objects.equals(account.getStoreId(),order.getStoreId())) throw invalid("赊销账户与原单归属不一致");
            BigDecimal credit=money(account.getCreditAmount(),"原赊销"), allocated=BigDecimal.ZERO;
            paid=money(account.getPaidAmount(),"累计实际还款");debt=money(account.getRemainingAmount(),"剩余欠款");
            if (credit.compareTo(originalCredit)!=0 || credit.subtract(paid).subtract(writtenOff).compareTo(debt)!=0) throw invalid("原单欠款与还款、退货冲账不守恒");
            for (var repayment : repayments) {
                if (repayment.getId()==null || !java.util.Objects.equals(repayment.getCreditAccountId(),account.getId())
                        || !java.util.Objects.equals(repayment.getCustomerId(),order.getCustomerId())
                        || !java.util.Objects.equals(repayment.getStoreId(),order.getStoreId())) throw invalid("历史还款缺少明确原单分配，须核对");
                BigDecimal amount=money(repayment.getAmount(),"原单还款"),refunded=counter(repayment.getRefundedAmount(),amount);
                String name=paymentName(repayment.getPaymentMethod(),true);
                allocated=allocated.add(amount);sourceRefunded=sourceRefunded.add(refunded);
                sources.add(new Source(null,repayment,"CREDIT_REPAYMENT",repayment.getId(),name,amount,refunded,repayment.getCreatedAt()));
            }
            if (allocated.compareTo(paid)!=0) throw invalid("原单还款分配与累计实际还款不一致，不能猜测历史来源");
            state.append("|CA:").append(account.getId()).append(':').append(credit).append(':').append(paid).append(':').append(debt);
        } else if (originalCredit.signum()!=0 || writtenOff.signum()!=0) throw invalid("原单缺少赊销账户");
        if (net.compareTo(originalCash.add(originalCredit))!=0 || returned.compareTo(net)>0
                || sourceRefunded.compareTo(cashReturned)!=0 || returned.compareTo(writtenOff.add(cashReturned))!=0
                || net.subtract(returned).compareTo(originalCash.add(paid).subtract(cashReturned).add(debt))!=0) {
            throw invalid("原成交、收款、欠款及退货事实不守恒，须先核对");
        }
        sources.sort(java.util.Comparator.comparing(Source::createdAt,java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())).reversed()
                .thenComparing(Source::type).thenComparing(Source::id,java.util.Comparator.reverseOrder()));
        for (Source source:sources) state.append('|').append(source.type()).append(':').append(source.id()).append(':').append(source.amount()).append(':').append(source.refunded()).append(':').append(source.methodName()).append(':').append(source.createdAt());
        return new Funding(order,account,payments,sources,debt,returned,state.toString());
    }

    /** 完成成交冲销及全部实物退回后才恢复券，不能保留0元或尾分物料复用券。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void restoreCouponIfFullyReturned(Order order) {
        List<com.agri_system.domain.after_sales.model.AfterSales> finished=afterSalesRepository.findByOrderId(order.getId()).stream()
                .filter(a->Integer.valueOf(com.agri_system.domain.after_sales.model.AfterSales.STATUS_FINISHED).equals(a.getStatus())).toList();
        BigDecimal returned=finished.stream().map(com.agri_system.domain.after_sales.model.AfterSales::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(returned.compareTo(order.getPayAmount())!=0) return;
        {
            List<OrderItem> goods=itemRepository.findByOrderId(order.getId());
            if(goods.isEmpty()) return;
            java.util.Map<Long,BigDecimal> quantities=new java.util.HashMap<>();
            for(var fact:finished) for(var item:afterSalesItemRepository.findByAfterSalesId(fact.getId())) {
                if(item.getQuantity()==null || item.getOrderItemId()==null) throw invalid("历史实退数量不明，不能恢复券");
                quantities.merge(item.getOrderItemId(),item.getQuantity(),BigDecimal::add);
            }
            if(goods.stream().anyMatch(item->quantities.getOrDefault(item.getId(),BigDecimal.ZERO).compareTo(item.getQuantity())!=0)) return;
        }
        for(OrderPayment payment:paymentRepository.findByOrderIdForUpdate(order.getId())) {
            if(Integer.valueOf(PaymentMethod.COUPON.getCode()).equals(payment.getPaymentType()) && payment.getCouponId()!=null) {
                couponRepository.findByIdForUpdate(payment.getCouponId()).ifPresent(coupon->{
                    if(java.util.Objects.equals(coupon.getUsedOrderId(),order.getId()) && coupon.restore()) couponRepository.save(coupon);
                });
            }
        }
    }

    /** 查询展示不改写历史未知拆分；NULL仍表示须核对。 */
    public FinancialSummary financialSummary(Order order) {
        var account=creditAccountRepository.findByOrderId(order.getId()).orElse(null);
        BigDecimal written=BigDecimal.ZERO,cash=BigDecimal.ZERO;
        for(var fact:afterSalesRepository.findByOrderId(order.getId())) {
            if(!Integer.valueOf(com.agri_system.domain.after_sales.model.AfterSales.STATUS_FINISHED).equals(fact.getStatus())) continue;
            written=written==null || fact.getCreditReductionAmount()==null ? null : written.add(fact.getCreditReductionAmount());
            cash=cash==null || fact.getCashRefundAmount()==null ? null : cash.add(fact.getCashRefundAmount());
        }
        return new FinancialSummary(written,cash,account==null?BigDecimal.ZERO:account.getRemainingAmount(),account==null?BigDecimal.ZERO:account.getPaidAmount());
    }
    private BigDecimal counter(BigDecimal value,BigDecimal original) {
        BigDecimal amount=money(value,"历史来源累计已退（未知需核对）");
        if(amount.compareTo(original)>0) throw invalid("来源累计已退不能超过原金额");
        return amount;
    }
    private BigDecimal money(BigDecimal value,String label) {
        if(value==null || value.signum()<0 || value.stripTrailingZeros().scale()>2) throw invalid(label+"必须为明确的非负分金额");
        return value.setScale(2);
    }
    private String paymentName(Integer type,boolean repayment) {
        if(type==null) throw invalid("原资金来源收款方式未知");
        return switch(type) {
            case 1 -> "现金";
            case 2 -> "微信";
            case 3 -> "支付宝";
            case 4 -> repayment?"银行转账":"本金余额";
            case 8 -> {if(repayment)throw invalid("还款来源方式未知");yield "专用金";}
            default -> throw invalid("原资金来源收款方式未知，不能自动退款");
        };
    }
    private IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}

    /**
     * 退回优惠券（仅全额退款时调用）：券是否实际恢复为"未使用"由
     * {@link CustomerCoupon#restore()} 按"已使用且未过期"判定；不满足则不改动、不保存。
     */
    private void restoreCoupon(Long couponId) {
        couponRepository.findByIdForUpdate(couponId).ifPresent(coupon -> {
            if (coupon.restore()) {
                couponRepository.save(coupon);
            }
        });
    }
}
