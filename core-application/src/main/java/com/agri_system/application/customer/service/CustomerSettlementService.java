package com.agri_system.application.customer.service;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        boolean isCreditOrder = order.getPaymentMethod() != null && order.getPaymentMethod().equals(PaymentMethod.CREDIT.getCode());
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
}






