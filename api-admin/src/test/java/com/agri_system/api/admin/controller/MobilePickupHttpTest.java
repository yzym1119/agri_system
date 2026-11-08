package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.service.MobilePickupTaskService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobilePickupHttpTest {
    @Test void readOnlyOrdersUseActualEmployeeAndPreserveExactStringIds() throws Exception {
        var tasks=mock(MobilePickupTaskService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobilePickupController(tasks)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(tasks.listOrders(8L,"PENDING",null)).thenReturn(new MobilePickupTaskService.Orders("3","PENDING",List.of(),null));
        String id="9223372036854775807";
        when(tasks.preview(Long.MAX_VALUE,8L)).thenReturn(new MobilePickupTaskService.Preview("3",id,"50","ORDER50","测试商品","FULFILLED",false,null,null,List.of(),"2026-11-01T10:00"));
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(8L);
            mvc.perform(get("/api/admin/mobile/pickup/orders").param("staffId","99").param("storeId","9"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.storeId").value("3")).andExpect(jsonPath("$.data").doesNotExist());
            mvc.perform(get("/api/admin/mobile/pickup/tasks/"+id)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.requestId").value(id))
                    .andExpect(jsonPath("$.allocations").isEmpty());
            verify(tasks).listOrders(8L,"PENDING",null);verify(tasks).preview(Long.MAX_VALUE,8L);
        }
    }
}
