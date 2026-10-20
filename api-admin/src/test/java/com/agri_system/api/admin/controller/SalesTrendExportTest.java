package com.agri_system.api.admin.controller;

import com.agri_system.application.report.dto.SalesTrendVO;
import com.agri_system.application.report.service.ReportAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SalesTrendExportTest {
    @Test void exportKeepsNetSalesButDoesNotPublishUnknownCostAsProfit() throws Exception {
        var service=mock(ReportAnalysisService.class);var controller=new ReportSalesController(service);
        var row=new SalesTrendVO();row.setDate(LocalDate.of(2026,10,19));row.setSaleAmount(new BigDecimal("60"));
        row.setCostAmount(new BigDecimal("30"));row.setProfit(new BigDecimal("30"));row.setProfitRate(new BigDecimal("50"));
        row.setCostWarnings(List.of("退货缺原回库证据"));
        when(service.salesTrend(any(),any(),any())).thenReturn(List.of(row));
        var response=new MockHttpServletResponse();
        controller.exportSalesTrend(row.getDate().atStartOfDay(),row.getDate().plusDays(1).atStartOfDay(),null,response);
        List<Map<Integer,String>> data=com.alibaba.excel.EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray())).sheet().doReadSync();
        assertEquals("60",data.get(0).get(1));assertNull(data.get(0).get(2));
        assertNull(data.get(0).get(3));assertNull(data.get(0).get(4));
        assertEquals(new BigDecimal("30"),row.getCostAmount(),"导出不改服务返回的原分桶对象");
    }
}
