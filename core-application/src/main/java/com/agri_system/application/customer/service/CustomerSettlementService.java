package com.agri_system.application.customer.service;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户结算服务（订单回调，积分取整，统计更新）
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class CustomerSettlementService {
    
    private final CustomerDomainService customerDomainService;
    private final CustomerRepository customerRepository;
    private final CustomerWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    
    /**
     * ⛔️ 订单完成回调：发放积分 + 更新客户统计（Critical Fix）
     * 
     * 处理内容：
     * 1. 发放积分（向下取整）
     * 2. 更新客户累计订单数量
     * 3. 更新客户累计消费金额
     * 4. 更新最后订单时间
     * 
     * 注意：赊销订单不发放积分，但需要更新统计
     * 
     * @param order 订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void onOrderFinished(Order order) {
        if (order.getCustomerId() == null) {
            return; // 散客订单不处理
        }
        
        if (order.getPayAmount() == null || order.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        
        // 查询客户
        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("客户不存在: " + order.getCustomerId()));
        
        // ⛔️ 1. 更新客户统计（Critical Fix）
        updateCustomerStatistics(customer, order);
        
        // ⛔️ 2. 发放积分（赊销订单不发放积分）
        boolean isCreditOrder = order.getPaymentMethod() != null
                && order.getPaymentMethod().equals(OrderMainPaymentMethod.CREDIT.getCode());
        if (!isCreditOrder) {
            grantPoints(order);
        }
    }
    
    /**
     * 更新客户统计信息
     * 
     * @param customer 客户
     * @param order 订单
     */
    private void updateCustomerStatistics(Customer customer, Order order) {
        // 更新订单数量
        Integer currentCount = customer.getTotalOrderCount();
        customer.setTotalOrderCount((currentCount != null ? currentCount : 0) + 1);
        
        // 更新累计消费金额
        BigDecimal currentAmount = customer.getTotalOrderAmount();
        BigDecimal newAmount = (currentAmount != null ? currentAmount : BigDecimal.ZERO)
                .add(order.getPayAmount());
        customer.setTotalOrderAmount(newAmount);
        
        // 更新最后订单时间
        customer.setLastOrderTime(order.getCreatedAt());
        
        // 保存客户信息
        customerRepository.save(customer);
    }
    
    /**
     * 发放积分
     * 
     * @param order 订单
     */
    private void grantPoints(Order order) {
        // 计算积分（向下取整）
        Integer points = customerDomainService.calculatePoints(order.getPayAmount());
        if (points <= 0) {
            return;
        }
        
        // 查询或创建积分钱包
        CustomerWallet pointsWallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(
                order.getCustomerId(), 3)
                .orElseGet(() -> {
                    CustomerWallet newWallet = new CustomerWallet();
                    newWallet.setCustomerId(order.getCustomerId());
                    newWallet.setWalletType(3);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setFrozenBalance(BigDecimal.ZERO);
                    newWallet.setVersion(0);
                    return walletRepository.save(newWallet);
                });
        
        BigDecimal balanceBefore = pointsWallet.getBalance();
        pointsWallet.add(BigDecimal.valueOf(points));
        walletRepository.save(pointsWallet);
        
        // 记录积分流水
        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomerId(order.getCustomerId());
        transaction.setWalletType(3);
        transaction.setTransactionType(4); // 赠送
        transaction.setAmount(BigDecimal.valueOf(points));
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(pointsWallet.getBalance());
        transaction.setReferenceType("ORDER");
        transaction.setReferenceId(order.getId());
        transaction.setRemark("订单完成赠送积分，订单金额: " + order.getPayAmount() + "，积分: " + points);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(null);
        transactionRepository.save(transaction);
    }

    /**
     * ⛔️ 订单退款回调：回退积分 + 回退客户统计（与 onOrderFinished 对称）
     *
     * 处理内容：
     * 1. 按退款比例回退该订单已发放的积分（封顶 = 已发 - 已回退；不使积分钱包为负）
     * 2. 累计消费金额按本次退款金额回退
     * 3. 累计订单数：仅当订单变为全额退款（状态 8）时 -1
     *
     * 幂等：同一退款事件由 PerformanceEventListener 的 markEvent 保证只触发一次；
     *       多次部分退款通过“已发积分 - 已回退积分”封顶，避免累计超扣。
     *
     * @param order        订单（状态已更新为已退款/部分退款）
     * @param refundAmount 本次退款金额
     */
    @Transactional(rollbackFor = Exception.class)
    public void onOrderRefunded(Order order, BigDecimal refundAmount) {
        if (order.getCustomerId() == null) {
            return; // 散客订单未发积分、未更新统计，无需回退
        }
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0
                || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        if (customer == null) {
            return;
        }

        // 1. 回退客户统计（累计消费、累计订单数）
        reverseCustomerStatistics(customer, order, refundAmount);

        // 2. 回退积分（赊销订单当初未发放积分，跳过；统计仍需回退）
        boolean isCreditOrder = order.getPaymentMethod() != null
                && order.getPaymentMethod().equals(OrderMainPaymentMethod.CREDIT.getCode());
        if (!isCreditOrder) {
            BigDecimal ratio = refundAmount.divide(payAmount, 4, RoundingMode.HALF_UP);
            reversePoints(order, refundAmount, ratio);
        }
    }

    /**
     * 回退客户统计：累计消费按退款金额递减，全额退款时累计订单数 -1。
     * lastOrderTime 保留（记录最近一次交互时间，非消费指标）。
     */
    private void reverseCustomerStatistics(Customer customer, Order order, BigDecimal refundAmount) {
        BigDecimal currentAmount = customer.getTotalOrderAmount();
        BigDecimal newAmount = (currentAmount != null ? currentAmount : BigDecimal.ZERO)
                .subtract(refundAmount);
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            newAmount = BigDecimal.ZERO;
        }
        customer.setTotalOrderAmount(newAmount);

        // 订单状态 8 = 已退款（全额），仅此时累计订单数 -1
        if (order.getOrderStatus() != null && order.getOrderStatus() == 8) {
            Integer currentCount = customer.getTotalOrderCount();
            int newCount = (currentCount != null ? currentCount : 0) - 1;
            customer.setTotalOrderCount(Math.max(newCount, 0));
        }

        customerRepository.save(customer);
    }

    /**
     * 按退款比例回退该订单已发放的积分。
     * - granted   = 该订单 type=4(赠送) 流水合计
     * - 已回退     = 该订单 ORDER_REFUND 流水合计（绝对值）
     * - 本次回退   = min(granted × ratio, granted − 已回退)，再不超过当前积分余额（不为负）
     */
    private void reversePoints(Order order, BigDecimal refundAmount, BigDecimal ratio) {
        // 该订单实际发放的积分
        BigDecimal granted = BigDecimal.ZERO;
        List<WalletTransaction> grantTxns = transactionRepository.findByReference(
                order.getCustomerId(), 3, "ORDER", order.getId());
        if (grantTxns != null) {
            for (WalletTransaction t : grantTxns) {
                if (t.getTransactionType() != null && t.getTransactionType() == 4
                        && t.getAmount() != null) {
                    granted = granted.add(t.getAmount());
                }
            }
        }
        if (granted.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 该订单未发放过积分（如订单金额<1元）
        }

        // 已回退积分（用于封顶，避免多次部分退款累计超扣）
        BigDecimal alreadyReversed = BigDecimal.ZERO;
        List<WalletTransaction> reverseTxns = transactionRepository.findByReference(
                order.getCustomerId(), 3, "ORDER_REFUND", order.getId());
        if (reverseTxns != null) {
            for (WalletTransaction t : reverseTxns) {
                if (t.getAmount() != null) {
                    alreadyReversed = alreadyReversed.add(t.getAmount().abs());
                }
            }
        }
        BigDecimal deductible = granted.subtract(alreadyReversed);
        if (deductible.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 已全部回退
        }

        // 本次应回退（向下取整，避免单次比例取整导致超扣；deductible 兜底）
        BigDecimal intended = granted.multiply(ratio).setScale(0, RoundingMode.DOWN);
        if (intended.compareTo(deductible) > 0) {
            intended = deductible;
        }
        if (intended.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        CustomerWallet pointsWallet = walletRepository.findByCustomerIdAndWalletTypeWithLock(
                order.getCustomerId(), 3).orElse(null);
        if (pointsWallet == null) {
            return; // 无积分钱包（理论上 grantPoints 已创建）
        }

        // 不使积分钱包为负：客户已消耗的积分无法追回，仅扣减当前余额
        BigDecimal currentBalance = pointsWallet.getBalance() != null
                ? pointsWallet.getBalance() : BigDecimal.ZERO;
        BigDecimal actualReverse = intended;
        String note = "";
        if (actualReverse.compareTo(currentBalance) > 0) {
            actualReverse = currentBalance;
            note = "（客户已消耗部分积分，仅回退剩余余额 " + currentBalance.toPlainString() + "）";
        }
        if (actualReverse.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 余额为0，无可回退
        }

        BigDecimal balanceBefore = pointsWallet.getBalance();
        pointsWallet.deduct(actualReverse);
        walletRepository.save(pointsWallet);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomerId(order.getCustomerId());
        transaction.setWalletType(3);
        transaction.setTransactionType(5); // 扣除
        transaction.setAmount(actualReverse.negate());
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(pointsWallet.getBalance());
        transaction.setReferenceType("ORDER_REFUND");
        transaction.setReferenceId(order.getId());
        transaction.setRemark("订单退款回扣积分，退款金额: " + refundAmount
                + "，比例: " + ratio + "，回扣: " + actualReverse + note);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCreatedBy(null);
        transactionRepository.save(transaction);
    }
}





