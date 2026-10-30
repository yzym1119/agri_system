package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.dto.vo.AssignedDeliveryVO;
import com.agri_system.application.order.service.*;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignedDeliveryHttpTest {
    @Test void mobileDeliveryAlwaysUsesCurrentEmployeeAndStringIds() throws Exception {
        var tasks=mock(DeliveryTaskService.class);var orders=mock(OrderApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobileDeliveryController(tasks,orders)).setControllerAdvice(new GlobalExceptionHandler()).build();
        String key="a".repeat(64);
        when(tasks.listAssignedTasks(8L,"PENDING",null)).thenReturn(new AssignedDeliveryVO.Tasks("3","PENDING",List.of(),null));
        when(orders.confirmAssignedReceipt(42L,key,8L)).thenReturn(new AssignedDeliveryVO.Result("3","42","COMPLETED",null));
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(8L);
            mvc.perform(get("/api/admin/mobile/delivery/tasks").param("staffId","99").param("storeId","9"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.storeId").value("3")).andExpect(jsonPath("$.data").doesNotExist());
            mvc.perform(post("/api/admin/mobile/delivery/tasks/42/confirm").contentType("application/json").content("{\"confirmationKey\":\""+key+"\",\"staffId\":99}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value("42"));
            verify(tasks).listAssignedTasks(8L,"PENDING",null);verify(orders).confirmAssignedReceipt(42L,key,8L);
        }
    }
    @Test void missingOrBlankConfirmationKeyNeverReachesBusinessAction() throws Exception {
        var tasks=mock(DeliveryTaskService.class);var orders=mock(OrderApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobileDeliveryController(tasks,orders)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/admin/mobile/delivery/tasks/42/confirm").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(10001));verifyNoInteractions(orders);
    }
}
