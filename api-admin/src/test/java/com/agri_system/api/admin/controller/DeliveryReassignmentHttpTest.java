package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeliveryReassignmentHttpTest {
    @Test void previewAndActionBindUrlAndCurrentEmployeeAndKeepExactVersions() throws Exception {
        var orders=mock(OrderApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DeliveryReassignmentController(orders)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(orders.previewDeliveryReassignment(42L,8L)).thenReturn(new OrderApplicationService.ReassignmentPreview("3","42","9","9223372036854775806",true,null));
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(8L);
            mvc.perform(get("/api/admin/orders/42/reassignment")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.version").value("9223372036854775806"));
            mvc.perform(post("/api/admin/orders/42/reassign").contentType("application/json")
                    .content("{\"orderId\":99,\"staffId\":99,\"expectedVersion\":\"2\",\"targetStaffId\":\"10\",\"reason\":\"临时换人\"}"))
                    .andExpect(status().isOk());
            verify(orders).reassignDelivery(42L,2L,10L,"临时换人",8L);
        }
    }
    @Test void missingVersionOrReasonNeverCallsMutation() throws Exception {
        var orders=mock(OrderApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DeliveryReassignmentController(orders)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/admin/orders/42/reassign").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(10001));verifyNoInteractions(orders);
    }
    @Test void fractionalRevisionMustNotBeRoundedIntoAValidOriginalRevision() throws Exception {
        var orders=mock(OrderApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DeliveryReassignmentController(orders)).setControllerAdvice(new GlobalExceptionHandler()).build();
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(8L);
            mvc.perform(post("/api/admin/orders/42/reassign").contentType("application/json")
                    .content("{\"expectedVersion\":0.5,\"targetStaffId\":9,\"reason\":\"换人\"}"))
                    .andExpect(jsonPath("$.code").value(10001));
            verifyNoInteractions(orders);
        }
    }
}
