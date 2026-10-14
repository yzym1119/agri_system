package com.agri_system.api.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.after_sales.service.AfterSalesApplicationService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证实际请求校验和URL绑定；不作为真实登录/权限测试。 */
class AfterSalesPathValidationTest {
    private final AfterSalesApplicationService service = mock(AfterSalesApplicationService.class);

    private MockMvc mvc() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new AfterSalesAdminController(service))
                .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void auditUsesPathIdWithoutRepeatingIdInBody() throws Exception {
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            mvc().perform(post("/api/admin/after-sales/42/audit").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"approved\":true}")).andExpect(status().isOk()).andExpect(content().string(""));
            var command = ArgumentCaptor.forClass(AfterSalesAuditCommand.class);
            verify(service).audit(command.capture(), eq(7L));
            assertEquals(42L, command.getValue().getAfterSalesId());
        }
    }

    @Test void refundUsesPathIdWithoutRepeatingIdInBody() throws Exception {
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            mvc().perform(post("/api/admin/after-sales/42/refund").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quoteId\":\"confirmed\",\"refundAmount\":30}")).andExpect(status().isOk()).andExpect(content().string(""));
            var command = ArgumentCaptor.forClass(AfterSalesRefundCommand.class);
            verify(service).refund(command.capture(), eq(7L));
            assertEquals(42L, command.getValue().getAfterSalesId());
        }
    }

    @Test void bodyCannotReplaceTheSelectedPathId() throws Exception {
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            mvc().perform(post("/api/admin/after-sales/42/refund").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"afterSalesId\":99,\"quoteId\":\"confirmed\"}")).andExpect(status().isOk());
            var command = ArgumentCaptor.forClass(AfterSalesRefundCommand.class);
            verify(service).refund(command.capture(), eq(7L));
            assertEquals(42L, command.getValue().getAfterSalesId());
        }
    }

    @Test void missingAuditDecisionStillRejectsBeforeService() throws Exception {
        mvc().perform(post("/api/admin/after-sales/42/audit").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @Test void missingRefundPreviewStillRejectsBeforeService() throws Exception {
        mvc().perform(post("/api/admin/after-sales/42/refund").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refundAmount\":30}")).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }
}
