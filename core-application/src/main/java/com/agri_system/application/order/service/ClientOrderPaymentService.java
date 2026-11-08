package com.agri_system.application.order.service;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.application.order.dto.command.OrderExternalPaymentCallbackCommand;
import com.agri_system.application.order.dto.command.OrderExternalPaymentConfirmCommand;
import com.agri_system.application.order.dto.command.OrderPayCommand;
import com.agri_system.application.order.dto.vo.OrderExternalPaymentIntentVO;
import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.application.order.dto.vo.OrderPaymentVO;
import com.agri_system.application.order.exception.OrderErrorCode;
import com.agri_system.domain.customer.repository.CustomerRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 客户端支付与首次付款完成编排；不实现平台验签，不新增真实支付开关。 */
@Slf4j
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
    private final PlatformTransactionManager transactionManager;
    @Value("${payment.client-confirm.enabled:false}")
    private boolean clientPaymentConfirmEnabled;

    /** 过期标记专用独立事务：外层确认事务随后抛异常回滚，但「意图已过期」的事实必须持久保留。
     *  条件更新 WHERE status=1：并发回调下不用先前读取的整行覆盖其他事务刚写入的新状态。
     *  返回 0 表示状态已被并发改变（已确认或已失效），由调用方按最新状态重新分流，不直接抛底层异常。 */
    private int expireIntentInNewTransaction(String intentNo) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Integer updated = template.execute(status -> paymentIntentRepository.expirePendingByIntentNo(intentNo));
        return updated == null ? 0 : updated;
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OrderPayPreviewVO payOrder(OrderPayCommand command, Long customerId) {
        // 内部支付（余额/专用金/券）不落独立请求号：订单行锁串行化并发提交，
        // 完成后再来的同参数请求由 isCompletedClientPayment 判为原成功重放，不会二次扣款；
        // 参数被改动则拒绝并提示订单状态，不做静默补扣。
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

        // 客户改用内部资金结清本单：先作废该订单所有待确认外部意图，避免同一订单并行两个支付尝试。
        // 平台侧交易无法在此撤销，若渠道稍后真实扣款，回调会因意图已失效进入人工对账告警分支。
        int expiredIntents = paymentIntentRepository.expirePendingByOrderId(order.getId());
        if (expiredIntents > 0) {
            log.warn("内部支付结清前作废待确认外部意图，需核对渠道是否已扣款: orderId={}, count={}",
                    order.getId(), expiredIntents);
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
     * 锁顺序约定（全服务统一为「订单行锁 → 支付意图行锁」，避免与确认路径互等死锁）：
     * 先取订单行锁串行化同订单的并发发起，再做幂等重放/旧意图失效/新意图落库。
     * 幂等键 requestKey 必填：无键即无法区分「重试」与「新发起」，重复或网络重试请求必须重放同一意图，
     * 绝不作废已创建的待确认意图。
     */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public OrderExternalPaymentIntentVO createExternalPaymentIntent(OrderPayCommand command, Long customerId) {
        String requestKey = normalizeRequestKey(command.getRequestKey());
        // 订单维度状态协调：行锁串行化并发发起，两个并发请求不可能各建一个待确认意图
        Order order = orderRepository.findByIdForUpdate(command.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        validatePendingCustomerOrder(order, customerId);

        OrderPaymentIntent existing = paymentIntentRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            // 先确认键归属：过期判断会写库，不能在未校验订单/客户前对他人意图产生副作用
            validateReplayOwnership(existing, order, customerId);
            // 幂等重放：再判原意图是否仍可支付，最后核参数。已完成/已失效/已过期一律不返回旧信息。
            validateIntentReplayable(existing);
            validateRequestKeyReplay(existing, order, command);
            // 预览按意图保存的资金快照重建，保证重放响应与首次返回一致，不掺入本次请求参数
            return toIntentVO(existing, order, previewFromSnapshot(existing));
        }

        OrderPayPreviewVO preview = buildClientPaymentPreview(order, command);
        if (preview.getExternalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 当前订单无需外部支付");
        }

        paymentIntentRepository.expirePendingByOrderId(order.getId());

        OrderPaymentIntent intentRecord = new OrderPaymentIntent();
        intentRecord.setIntentNo(generatePaymentIntentNo(order.getId()));
        intentRecord.setRequestKey(requestKey);
        intentRecord.setOrderId(order.getId());
        intentRecord.setCustomerId(customerId);
        intentRecord.setUseBalance(Boolean.TRUE.equals(command.getUseBalance()));
        intentRecord.setUseSpecialFund(Boolean.TRUE.equals(command.getUseSpecialFund()));
        intentRecord.setCouponId(command.getCouponId());
        intentRecord.setExternalPaymentType(OrderPaymentService.resolveExternalPaymentType(command.getExternalPaymentType()));
        intentRecord.setExternalAmount(preview.getExternalAmount());
        // 资金拆分快照：重放按此返回，确认按此核对，不能事后从钱包当前余额反推
        intentRecord.setBalanceAmount(nonNull(preview.getBalanceAmount()));
        intentRecord.setSpecialFundAmount(nonNull(preview.getSpecialFundAmount()));
        intentRecord.setCouponAmount(nonNull(preview.getCouponAmount()));
        intentRecord.setStatus(1);
        intentRecord.setExpireAt(LocalDateTime.now().plusMinutes(15));
        intentRecord.setCreatedAt(LocalDateTime.now());
        intentRecord.setUpdatedAt(LocalDateTime.now());
        paymentIntentRepository.save(intentRecord);

        return toIntentVO(intentRecord, order, preview);
    }

    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": 缺少支付发起幂等键 requestKey，无法保证支付意图不重复");
        }
        if (requestKey.length() > 64) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": requestKey 不能超过64字符");
        }
        return requestKey;
    }

    /** 重放前确认原意图仍可支付：只允许 PENDING(1) 且未过期，其余状态不得把旧意图返回给客户端。 */
    private void validateIntentReplayable(OrderPaymentIntent intent) {
        Integer status = intent.getStatus();
        if (Integer.valueOf(2).equals(status)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": 支付意图已完成，请勿重复支付");
        }
        if (!Integer.valueOf(1).equals(status)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": 支付意图已失效，请更换 requestKey 后重新发起支付");
        }
        if (intent.getExpireAt() != null && intent.getExpireAt().isBefore(LocalDateTime.now())) {
            // 独立事务条件更新持久化过期标记：本次重放被拒绝，但过期事实不能随当前事务回滚丢失
            if (expireIntentInNewTransaction(intent.getIntentNo()) == 0) {
                // 并发下已被确认或已被替代：不再声称「刚过期」，按最新状态给出明确结论
                throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                        + ": 支付意图状态已变化，请更换 requestKey 后重新发起支付");
            }
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": 支付意图已过期，请更换 requestKey 后重新发起支付");
        }
    }

    /** 重放响应的预览按意图保存的资金快照重建，不重新读钱包/券：充值或消费后同一 requestKey 仍返回第一次的拆分。 */
    private OrderPayPreviewVO previewFromSnapshot(OrderPaymentIntent intent) {
        BigDecimal balance = nonNull(intent.getBalanceAmount());
        BigDecimal specialFund = nonNull(intent.getSpecialFundAmount());
        BigDecimal coupon = nonNull(intent.getCouponAmount());
        BigDecimal external = nonNull(intent.getExternalAmount());

        List<OrderPaymentVO> payments = new ArrayList<>();
        if (coupon.signum() > 0) {
            payments.add(paymentLine(PaymentMethod.COUPON.getCode(), coupon, intent.getCouponId()));
        }
        if (specialFund.signum() > 0) {
            payments.add(paymentLine(PaymentMethod.SPECIAL_FUND.getCode(), specialFund, null));
        }
        if (balance.signum() > 0) {
            payments.add(paymentLine(PaymentMethod.BALANCE.getCode(), balance, null));
        }
        if (external.signum() > 0) {
            payments.add(paymentLine(intent.getExternalPaymentType(), external, null));
        }

        OrderPayPreviewVO preview = new OrderPayPreviewVO();
        preview.setTotalAmount(balance.add(specialFund).add(coupon).add(external));
        preview.setCouponAmount(coupon);
        preview.setSpecialFundAmount(specialFund);
        preview.setBalanceAmount(balance);
        preview.setExternalAmount(external);
        preview.setPayments(payments);
        return preview;
    }

    private OrderPaymentVO paymentLine(Integer paymentType, BigDecimal amount, Long couponId) {
        OrderPaymentVO payment = new OrderPaymentVO();
        payment.setPaymentType(paymentType);
        payment.setAmount(amount);
        payment.setCouponId(couponId);
        return payment;
    }

    private BigDecimal nonNull(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    /** 幂等键归属校验：键只能被原订单、原客户复用，防止键冲突把他人意图信息或状态变更带出来。 */
    private void validateReplayOwnership(OrderPaymentIntent existing, Order order, Long customerId) {
        if (!order.getId().equals(existing.getOrderId()) || !customerId.equals(existing.getCustomerId())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": requestKey 已被其他订单或客户占用，请更换后重试");
        }
    }

    /** 幂等键冲突校验：支付选项与「快照合计=订单应付」必须一致，避免同键换选项或换价后被静默接受。 */
    private void validateRequestKeyReplay(OrderPaymentIntent existing, Order order, OrderPayCommand command) {
        if (!samePaymentOptions(existing, command)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": requestKey 对应的支付选项已变化，请更换 requestKey 后重新发起");
        }
        // 不比对实时余额/券：客户充值或消费会让重新预览的外部金额变化，但那不是新请求，
        // 重放必须返回第一次的结果。这里只确认订单本身的应付没被改价，快照仍与订单一致。
        BigDecimal snapshotTotal = nonNull(existing.getBalanceAmount()).add(nonNull(existing.getSpecialFundAmount()))
                .add(nonNull(existing.getCouponAmount())).add(nonNull(existing.getExternalAmount()));
        if (order.getPayAmount() == null || snapshotTotal.compareTo(order.getPayAmount()) != 0) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": requestKey 对应的支付意图金额已变化，请更换 requestKey 后重新发起");
        }
    }

    /** 余额/专用金/券/外部渠道逐项比对；金额相同不代表选项相同（余额为 0、等价券互换等）。 */
    private boolean samePaymentOptions(OrderPaymentIntent existing, OrderPayCommand command) {
        return Boolean.TRUE.equals(existing.getUseBalance()) == Boolean.TRUE.equals(command.getUseBalance())
                && Boolean.TRUE.equals(existing.getUseSpecialFund()) == Boolean.TRUE.equals(command.getUseSpecialFund())
                && java.util.Objects.equals(existing.getCouponId(), command.getCouponId())
                && java.util.Objects.equals(existing.getExternalPaymentType(),
                        OrderPaymentService.resolveExternalPaymentType(command.getExternalPaymentType()));
    }

    private OrderExternalPaymentIntentVO toIntentVO(OrderPaymentIntent intentRecord, Order order,
                                                    OrderPayPreviewVO preview) {
        OrderExternalPaymentIntentVO intent = new OrderExternalPaymentIntentVO();
        intent.setIntentNo(intentRecord.getIntentNo());
        intent.setOrderId(order.getId());
        intent.setOrderNo(order.getOrderNo());
        intent.setExternalPaymentType(intentRecord.getExternalPaymentType());
        intent.setExternalAmount(intentRecord.getExternalAmount());
        intent.setExpireAt(intentRecord.getExpireAt());
        intent.setPreview(preview);
        return intent;
    }

    /** 发起路径与确认路径共用的订单归属与状态校验（不带锁版本，锁由调用方持有）。 */
    private void validatePendingCustomerOrder(Order order, Long customerId) {
        if (order.getOrderStatus() == null || order.getOrderStatus() != 1) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_STATUS_INVALID + ": 订单状态不正确");
        }
        if (order.getCustomerId() == null || !order.getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 无权操作此订单");
        }
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
        // 回调核验（V140）：商户订单号由 intentNo 绑定订单体现；实付金额/状态在 confirmExternalPaymentIntent 中
        // 经 preview 比对与条件锁定核验；此处补齐支付平台一致性——确认声明的渠道必须与发起意图一致，
        // 防止用支付宝流水确认微信意图。平台订单号/币种/商户号字段待真实平台接入时随 V141 扩展。
        if (!java.util.Objects.equals(OrderPaymentService.resolveExternalPaymentType(command.getExternalPaymentType()),
                intent.getExternalPaymentType())) {
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": 支付方式与发起意图不一致，请核对支付渠道");
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
                        && !java.util.Objects.equals(source.getPaymentType(),OrderPaymentService.resolveExternalPaymentType(command.getExternalPaymentType()))) return false;
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
        // 渠道随请求一起进入预览：支付宝请求不得被标成微信
        return paymentService.previewPayment(
                order,
                command.getUseBalance(),
                command.getUseSpecialFund(),
                command.getCouponId(),
                command.getExternalPaymentType()
        );
    }

    /** 已确认同一流水→安全重放（返回 true）；其他流水确认或意图已失效→抛出，失效流水留痕供人工对账。 */
    private boolean settledOrReject(OrderPaymentIntent latest, String externalTransactionNo) {
        Integer status = latest.getStatus();
        if (Integer.valueOf(2).equals(status)) {
            if (externalTransactionNo.equals(latest.getExternalTransactionNo())) {
                return true;
            }
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 支付意图已被其他流水确认");
        }
        if (Integer.valueOf(3).equals(status)) {
            // 真实支付接通后该流水可能已在外部渠道扣款，不能静默忽略：
            // 留痕告警供人工对账退款，退款闭环上线前由运维按流水号核查。
            log.warn("已失效支付意图收到确认请求，外部流水未入账，需人工对账退款: intentNo={}, status={}, "
                            + "externalTransactionNo={}, externalAmount={}, orderId={}",
                    latest.getIntentNo(), status, externalTransactionNo,
                    latest.getExternalAmount(), latest.getOrderId());
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED
                    + ": 支付意图已失效（已过期或已被新支付替代），确认被拒绝；若该笔外部流水已实际扣款，"
                    + "请按流水号 " + externalTransactionNo + " 人工对账退款");
        }
        return false;
    }

    private void confirmExternalPaymentIntent(OrderPaymentIntent intent,
                                              String externalTransactionNo,
                                              LocalDateTime externalPaidAt,
                                              Long operatorId) {
        // 锁顺序（全服务统一「订单行锁 → 意图行锁」）：先持订单行锁，再 claim 意图行锁，
        // 与发起路径 createExternalPaymentIntent 一致，避免支付意图锁与订单锁相互等待死锁。
        Order order = orderRepository.findByIdForUpdate(intent.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(OrderErrorCode.ORDER_NOT_FOUND + ": 订单不存在"));
        // 最新状态优先分流：失效(3)的意图可能同时已过期，必须先按状态走对账告警，
        // 不能先做过期判断——那样条件更新 0 行会抛底层异常，真实扣款的流水就漏掉了对账入口。
        OrderPaymentIntent latest = paymentIntentRepository.findByIntentNo(intent.getIntentNo()).orElse(intent);
        if (settledOrReject(latest, externalTransactionNo)) {
            return;
        }
        if (latest.getExpireAt() != null && latest.getExpireAt().isBefore(LocalDateTime.now())) {
            // 独立事务条件更新持久化过期标记：外层事务随后回滚也不丢「该意图已过期」的审计事实
            if (expireIntentInNewTransaction(latest.getIntentNo()) == 0) {
                if (settledOrReject(paymentIntentRepository.findByIntentNo(latest.getIntentNo()).orElse(latest),
                        externalTransactionNo)) {
                    return;
                }
                throw new IllegalStateException("支付意图状态在并发中变化，请人工核对: intentNo=" + latest.getIntentNo());
            }
            throw new IllegalArgumentException(OrderErrorCode.ORDER_OPERATION_NOT_SUPPORTED + ": 支付意图已过期，请重新发起支付");
        }

        // 条件锁定：仅当意图仍为 PENDING(1) 时持有行锁至事务提交，串行化并发确认/过期/替代失效。
        // 严禁已失效(3，被新支付意图替代)的意图进入正常结算——其对应外部资金可能已扣款，必须走对账。
        if (paymentIntentRepository.claimPendingByIntentNo(intent.getIntentNo()) == 0) {
            if (settledOrReject(paymentIntentRepository.findByIntentNo(intent.getIntentNo()).orElse(latest),
                    externalTransactionNo)) {
                return;
            }
            throw new IllegalStateException("支付意图未能进入确认状态，请人工核对: intentNo=" + intent.getIntentNo());
        }

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
                intent.getCouponId(),
                intent.getExternalPaymentType()
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
