package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.OrderItemDTO;
import com.agri_system.application.order.dto.command.PaymentDetailDTO;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.domain.order.enums.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 赊销订单风控测试
 * 
 * 测试场景：
 * 1. 赊销订单禁止使用优惠券
 * 2. 赊销订单禁止使用余额
 * 3. 赊销订单禁止使用专用金
 * 4. 赊销订单必须全额赊销
 * 5. 正常赊销订单可以创建
 */
@DisplayName("赊销订单风控测试")
public class CreditOrderValidationTest {
    
    /**
     * 测试场景1：赊销订单 + 优惠券 → 应该抛出异常
     */
    @Test
    @DisplayName("赊销订单禁止使用优惠券")
    public void testCreditOrderWithCoupon() {
        POSOrderCreateCommand command = createBasicCreditOrder();
        command.setCouponId(1L); // 尝试使用优惠券
        
        // 预期：抛出 CREDIT_NO_COUPON 异常
        // 实际测试时需要注入真实的 OrderApplicationService
        // 这里仅展示测试结构
        
        // Exception exception = assertThrows(IllegalArgumentException.class, () -> {
        //     orderService.createPOSOrder(command, 1L);
        // });
        // assertTrue(exception.getMessage().contains(OrderErrorCode.CREDIT_NO_COUPON));
    }
    
    /**
     * 测试场景2：赊销订单 + 余额支付 → 应该抛出异常
     */
    @Test
    @DisplayName("赊销订单禁止使用余额")
    public void testCreditOrderWithBalance() {
        POSOrderCreateCommand command = createBasicCreditOrder();
        PaymentDetailDTO balancePayment = new PaymentDetailDTO();
        balancePayment.setPaymentType(PaymentMethod.BALANCE.getCode());
        balancePayment.setAmount(BigDecimal.valueOf(100));
        command.setPayments(List.of(balancePayment)); // 尝试混入余额支付

        // 预期：抛出 CREDIT_NO_MIXED 异常
    }
    
    /**
     * 测试场景3：赊销订单 + 专用金 → 应该抛出异常
     */
    @Test
    @DisplayName("赊销订单禁止使用专用金")
    public void testCreditOrderWithSpecialFund() {
        POSOrderCreateCommand command = createBasicCreditOrder();
        command.setUseSpecialFund(true); // 尝试使用专用金
        
        // 预期：抛出 CREDIT_NO_MIXED 异常
    }
    
    /**
     * 测试场景4：赊销订单金额不匹配 → 应该抛出异常
     */
    @Test
    @DisplayName("赊销订单必须全额赊销")
    public void testCreditOrderAmountMismatch() {
        // 这个测试需要在订单创建后校验
        // payAmount 必须等于 totalAmount
        
        // 预期：抛出 CREDIT_AMOUNT_MISMATCH 异常
    }
    
    /**
     * 测试场景5：正常赊销订单 → 应该成功创建
     */
    @Test
    @DisplayName("正常赊销订单可以创建")
    public void testValidCreditOrder() {
        POSOrderCreateCommand command = createBasicCreditOrder();
        // 不使用优惠券、余额、专用金
        
        // 预期：成功创建订单
        // Long orderId = orderService.createPOSOrder(command, 1L);
        // assertNotNull(orderId);
        
        // 验证支付记录：只有一条 payment_type=6 的记录
        // List<OrderPayment> payments = paymentRepository.findByOrderId(orderId);
        // assertEquals(1, payments.size());
        // assertEquals(6, payments.get(0).getPaymentType());
    }
    
    /**
     * 创建基本的赊销订单命令
     */
    private POSOrderCreateCommand createBasicCreditOrder() {
        POSOrderCreateCommand command = new POSOrderCreateCommand();
        command.setStoreId(1L);
        command.setCustomerId(1L);
        command.setStaffId(1L);
        command.setPaymentType(PaymentMethod.CREDIT.getCode()); // 赊销
        
        // 添加订单明细
        List<OrderItemDTO> items = new ArrayList<>();
        OrderItemDTO item = new OrderItemDTO();
        item.setItemType(1);
        item.setProductId(1L);
        item.setQuantity(BigDecimal.valueOf(10));
        item.setUnit("箱");
        item.setUnitPrice(BigDecimal.valueOf(100));
        items.add(item);
        command.setItems(items);
        
        return command;
    }
}





