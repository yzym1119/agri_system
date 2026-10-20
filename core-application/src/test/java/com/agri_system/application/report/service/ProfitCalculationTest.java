package com.agri_system.application.report.service;

import com.agri_system.application.report.dto.SalesTrendVO;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.repository.OrderItemBatchAllocationRepository;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.infrastructure.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 毛利计算准确性测试
 */
@ExtendWith(MockitoExtension.class)
class ProfitCalculationTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private InventoryBatchMapper inventoryBatchMapper;
    @Mock
    private CreditAccountMapper creditAccountMapper;
    @Mock
    private CreditRepaymentLogMapper repaymentLogMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private AfterSalesMapper afterSalesMapper;
    @Mock
    private OrderItemBatchAllocationRepository allocationRepository;

    private ReportAnalysisService reportAnalysisService;

    @BeforeEach
    void setUp() {
        reportAnalysisService = new ReportAnalysisService(
                orderMapper, orderItemMapper, inventoryBatchMapper,
                creditAccountMapper, repaymentLogMapper, customerMapper, productMapper, afterSalesMapper,
                allocationRepository);
    }

    @Test
    void profitCalculationUsesCostSnapshot() {
        // 准备测试数据
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 2, 0, 0);

        Order order = new Order();
        order.setId(1L);
        order.setPayAmount(new BigDecimal("250.00"));
        order.setOrderStatus(2); // 已支付
        order.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));

        OrderItem item1 = new OrderItem();
        item1.setId(11L);
        item1.setOrderId(1L);
        item1.setUnitPrice(new BigDecimal("100.00")); // 售价
        item1.setQuantity(new BigDecimal("2"));
        item1.setCostPriceSnapshot(new BigDecimal("60.00")); // 成本快照

        OrderItem item2 = new OrderItem();
        item2.setId(12L);
        item2.setOrderId(1L);
        item2.setUnitPrice(new BigDecimal("50.00"));
        item2.setQuantity(new BigDecimal("1"));
        item2.setCostPriceSnapshot(new BigDecimal("30.00"));

        when(orderMapper.selectListByQuery(any())).thenReturn(List.of(order));
        when(orderItemMapper.selectListByQuery(any())).thenReturn(List.of(item1, item2));

        // 执行
        List<SalesTrendVO> result = reportAnalysisService.salesTrend(start, end, null);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.size());
        SalesTrendVO vo = result.get(0);
        
        // 销售额 = 100*2 + 50*1 = 250
        assertEquals(0, new BigDecimal("250.00").compareTo(vo.getSaleAmount()));
        
        // 成本额 = 60*2 + 30*1 = 150
        assertEquals(0, new BigDecimal("150.00").compareTo(vo.getCostAmount()));
        
        // 毛利 = 250 - 150 = 100
        assertEquals(0, new BigDecimal("100.00").compareTo(vo.getProfit()));
        
        // 毛利率 = 100/250 * 100 = 40%
        assertEquals(0, new BigDecimal("40.00").compareTo(vo.getProfitRate()));
    }

    @Test
    void profitCalculationHandlesNullCostSnapshot() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 1, 2, 0, 0);

        Order order = new Order();
        order.setId(1L);
        order.setPayAmount(new BigDecimal("100.00"));
        order.setOrderStatus(2);
        order.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));

        OrderItem item = new OrderItem();
        item.setId(11L);
        item.setOrderId(1L);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setQuantity(new BigDecimal("1"));
        item.setCostPriceSnapshot(null); // 成本快照为空，应降级为0

        when(orderMapper.selectListByQuery(any())).thenReturn(List.of(order));
        when(orderItemMapper.selectListByQuery(any())).thenReturn(List.of(item));

        List<SalesTrendVO> result = reportAnalysisService.salesTrend(start, end, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        SalesTrendVO vo = result.get(0);
        
        // 销售额 = 100
        assertEquals(0, new BigDecimal("100.00").compareTo(vo.getSaleAmount()));
        
        // 成本额 = 0（因为costPriceSnapshot为null）
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getCostAmount()));
        
        // 毛利 = 100 - 0 = 100
        assertEquals(0, new BigDecimal("100.00").compareTo(vo.getProfit()));
    }
}



