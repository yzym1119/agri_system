package com.agri_system.api.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.order.dto.command.OrderShipCommand;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.application.order.service.PickupFulfillmentService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderShipPathValidationTest {
    @Test void shippingBodyNeedsNoDuplicateOrderIdAndUsesSelectedPath() throws Exception {
        var service=mock(OrderApplicationService.class);
        var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        var mvc=MockMvcBuilders.standaloneSetup(new OrderAdminController(service,
                mock(PickupFulfillmentService.class),mock(com.agri_system.application.order.service.PosSaleSubmissionService.class)))
                .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
        try(var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            mvc.perform(post("/api/admin/orders/42/ship").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryStaffId\":7}"))
                .andExpect(status().isOk()).andExpect(content().string(""));
            var command=ArgumentCaptor.forClass(OrderShipCommand.class);
            verify(service).shipOrder(command.capture(),eq(7L));
            assertEquals(42L,command.getValue().getOrderId());
        }
    }
    @Test void receiptSupportsLegacyEmptyBodiesAndForwardsExplicitConfirmation() throws Exception {
        var service=mock(OrderApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OrderAdminController(service,
                mock(PickupFulfillmentService.class),mock(com.agri_system.application.order.service.PosSaleSubmissionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        try(var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            mvc.perform(post("/api/admin/orders/42/confirm-receipt")).andExpect(status().isOk());
            mvc.perform(post("/api/admin/orders/42/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isOk());
            verify(service,times(2)).confirmReceipt(42L,7L);
            mvc.perform(post("/api/admin/orders/42/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmationKey\":\"checked-key\"}"))
                .andExpect(status().isOk());
            verify(service).confirmReceipt(42L,7L,"checked-key");
            mvc.perform(post("/api/admin/orders/42/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmationKey\":\" \"}"))
                .andExpect(jsonPath("$.code").value(com.agri_system.domain.common.error.SystemErrorCode.INVALID_ARGUMENT.getCode()))
                .andExpect(jsonPath("$.message").value("请先核对配送商品与收货信息"));
            verifyNoMoreInteractions(service);
        }
    }
}
