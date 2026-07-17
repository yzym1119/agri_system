package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 混合支付逻辑测试
 * 模拟100元订单 = 20券 + 30专用金 + 50微信的扣减计算
 * 
 * @author AI Generated
 */
@SpringBootTest(classes = com.agri_system.api.admin.AdminApplication.class)
@ActiveProfiles("test")
@Disabled("集成测试：需独立的 agri_system_test 测试库及种子数据，本地环境未配置")
@Transactional
public class MixPaymentLogicTest {
    
    @Autowired
    private OrderPaymentService paymentService;
    
    @Autowired
    private OrderItemRepository itemRepository;
    
    @Test
    void testMixPaymentCalculation() {
        // Given: 准备100元订单
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(1L);
        order.setPayAmount(new BigDecimal("100.00"));
        
        // 准备订单明细（部分商品允许专用金）
        OrderItem item1 = new OrderItem();
        item1.setOrderId(1L);
        item1.setTotalPrice(new BigDecimal("60.00"));
        item1.setAllowSpecialFund(1); // 允许专用金
        
        OrderItem item2 = new OrderItem();
        item2.setOrderId(1L);
        item2.setTotalPrice(new BigDecimal("40.00"));
        item2.setAllowSpecialFund(0); // 不允许专用金
        
        // When: 预运算支付（20券 + 30专用金 + 50微信）
        OrderPayPreviewVO preview = paymentService.previewPayment(order, true, true, 1L);
        
        // Then: 验证支付金额分配
        assertEquals(new BigDecimal("20.00"), preview.getCouponAmount(), "优惠券金额应为20");
        assertEquals(new BigDecimal("30.00"), preview.getSpecialFundAmount(), "专用金金额应为30");
        assertEquals(new BigDecimal("50.00"), preview.getExternalAmount(), "外部支付金额应为50");
    }
}







