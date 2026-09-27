package com.agri_system.application.report.service;

import com.agri_system.application.report.dto.ChurnRiskVO;
import com.agri_system.application.report.dto.CustomerRankVO;
import com.agri_system.domain.customer.model.Customer;
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
 * 客户分析测试（大户排行 + 流失预警）
 */
@ExtendWith(MockitoExtension.class)
class CustomerAnalysisTest {

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
    void topCustomersRankedByTotalOrderAmount() {
        // 数据库已按 total_order_amount DESC 排序（SQL ORDER BY）
        Customer c3 = new Customer();
        c3.setId(3L);
        c3.setName("大户C");
        c3.setPhone("13800000003");
        c3.setTotalOrderAmount(new BigDecimal("80000.00"));
        c3.setLastOrderTime(LocalDateTime.now().minusDays(1));

        Customer c1 = new Customer();
        c1.setId(1L);
        c1.setName("大户A");
        c1.setPhone("13800000001");
        c1.setTotalOrderAmount(new BigDecimal("50000.00"));
        c1.setLastOrderTime(LocalDateTime.now().minusDays(10));

        Customer c2 = new Customer();
        c2.setId(2L);
        c2.setName("大户B");
        c2.setPhone("13800000002");
        c2.setTotalOrderAmount(new BigDecimal("30000.00"));
        c2.setLastOrderTime(LocalDateTime.now().minusDays(5));

        when(customerMapper.selectListByQuery(any())).thenReturn(List.of(c3, c1, c2));

        List<CustomerRankVO> result = reportAnalysisService.topCustomers(20, null);

        assertNotNull(result);
        assertEquals(3, result.size());
        // SQL 已排序：C(80000) > A(50000) > B(30000)
        assertEquals(3L, result.get(0).getCustomerId());
        assertEquals(1L, result.get(1).getCustomerId());
        assertEquals(2L, result.get(2).getCustomerId());
    }

    @Test
    void churnRisksFilteredByLastOrderTimeAndAmount() {
        LocalDateTime now = LocalDateTime.now();

        // SQL WHERE clause filters: last_order_time < 90天前 AND total_order_amount > 1000
        // 只有 churn1 和 churn2 符合 SQL 筛选条件
        Customer churn1 = new Customer();
        churn1.setId(101L);
        churn1.setName("流失客户1");
        churn1.setPhone("13900000001");
        churn1.setTotalOrderAmount(new BigDecimal("5000.00"));
        churn1.setLastOrderTime(now.minusDays(120)); // 120天前 < 90天 ✓, 5000 > 1000 ✓

        Customer churn2 = new Customer();
        churn2.setId(102L);
        churn2.setName("流失客户2");
        churn2.setPhone("13900000002");
        churn2.setTotalOrderAmount(new BigDecimal("2000.00"));
        churn2.setLastOrderTime(now.minusDays(100)); // 100天前 < 90天 ✓, 2000 > 1000 ✓

        // SQL 已筛选：只返回符合 WHERE 条件的数据
        when(customerMapper.selectListByQuery(any())).thenReturn(List.of(churn1, churn2));

        List<ChurnRiskVO> result = reportAnalysisService.churnRisks(null);

        assertNotNull(result);
        assertEquals(2, result.size());

        // 应该按流失天数降序：churn1(120天) > churn2(100天)
        assertEquals(101L, result.get(0).getCustomerId());
        assertEquals(120L, result.get(0).getDaysSinceLastOrder());
        assertEquals(102L, result.get(1).getCustomerId());
        assertEquals(100L, result.get(1).getDaysSinceLastOrder());
    }
}



