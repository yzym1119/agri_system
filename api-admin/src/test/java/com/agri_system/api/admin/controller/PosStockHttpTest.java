package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.order.dto.command.PosStockCheckCommand;
import com.agri_system.application.order.service.PosStockPreviewService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PosStockHttpTest {
    @Test void rawReadContractAndSalePermissionAreExplicit() throws Exception {
        var stock=mock(PosStockPreviewService.class);
        when(stock.preview(any())).thenAnswer(inv->{PosStockCheckCommand c=inv.getArgument(0);assertEquals(10L,c.getItems().get(0).getProductId());return new PosStockPreviewService.Preview("1",true,List.of());});
        var mvc=MockMvcBuilders.standaloneSetup(new PosStockController(stock)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/admin/pos/catalog/stock-preview").contentType("application/json")
                .content("{\"storeId\":1,\"items\":[{\"productId\":\"10\",\"quantity\":\"1\",\"unit\":\"袋\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value("1"))
                .andExpect(jsonPath("$.sufficient").value(true)).andExpect(jsonPath("$.issues").isEmpty());
        assertArrayEquals(new String[]{"pos:order:create"},PosStockController.class.getAnnotation(SaCheckPermission.class).value());
    }
    @Test void emptyAndNullRowsDoNotReachBusinessService() throws Exception {
        var stock=mock(PosStockPreviewService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new PosStockController(stock)).setControllerAdvice(new GlobalExceptionHandler()).build();
        for(String json:List.of("{\"items\":[]}","{\"items\":[null]}"))
            mvc.perform(post("/api/admin/pos/catalog/stock-preview").contentType("application/json").content(json)).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(stock);
    }
}
