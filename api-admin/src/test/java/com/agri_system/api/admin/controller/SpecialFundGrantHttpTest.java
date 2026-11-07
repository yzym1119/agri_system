package com.agri_system.api.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.customer.service.CustomerApplicationService;
import com.agri_system.application.customer.service.WalletRechargeService;
import com.agri_system.application.marketing.service.CouponService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 手动发专用金的真实 MVC 边界：@Valid 不再空转，非法金额不触达钱包服务。 */
class SpecialFundGrantHttpTest {

    private static final String URL = "/api/admin/customer/grant-special-fund";

    private record Fixture(MockMvc mvc, WalletApplicationService wallet, CustomerBenefitLogRepository benefitLog) {}

    private static Fixture fixture() {
        var wallet = mock(WalletApplicationService.class);
        var benefitLog = mock(CustomerBenefitLogRepository.class);
        var controller = new CustomerAdminController(
                mock(CustomerApplicationService.class), mock(WalletRechargeService.class), wallet,
                mock(CouponTemplateRepository.class), mock(CustomerCouponRepository.class),
                mock(CustomerDomainService.class), benefitLog, mock(CouponService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        return new Fixture(mvc, wallet, benefitLog);
    }

    @Test
    void malformedAmountsAreRejectedBeforeAnyWalletWrite() throws Exception {
        Fixture f = fixture();
        for (String body : new String[]{
                "{\"amount\":1.00}",                       // 缺 customerId
                "{\"customerId\":9}",                      // 缺 amount
                "{\"customerId\":9,\"amount\":0}",         // 非正数
                "{\"customerId\":9,\"amount\":-1.00}",
                "{\"customerId\":9,\"amount\":0.001}",     // 超两位小数
                "{\"customerId\":9,\"amount\":1.005}",
                "{\"customerId\":9,\"amount\":1.00,\"remark\":\"" + "x".repeat(501) + "\"}"}) {
            f.mvc().perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10001));
        }
        verifyNoInteractions(f.wallet(), f.benefitLog());
    }

    @Test
    void overLimitAmountIsRejectedByServiceGuardBeforeAnyWrite() throws Exception {
        Fixture f = fixture();
        // 服务层防线用真实实现语义模拟：超上限直接抛业务异常
        when(f.wallet().addSpecialFund(anyLong(), any(BigDecimal.class), anyString(), isNull(), any(), any()))
                .thenThrow(new IllegalArgumentException("发放金额必须大于0、最多两位小数，且单次不超过5000.00元"));
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            f.mvc().perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":9,\"amount\":5000.01}"))
                    .andExpect(jsonPath("$.code").value(10001))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("5000.00")));
        }
        verifyNoInteractions(f.benefitLog());
    }

    @Test
    void validGrantStillWritesWalletAndBenefitLogWithRealStaffIdentity() throws Exception {
        Fixture f = fixture();
        when(f.wallet().addSpecialFund(eq(9L), any(BigDecimal.class), eq("MANUAL_GRANT"), isNull(), eq(7L), eq("活动补发")))
                .thenReturn(new BigDecimal("100.00"));
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            f.mvc().perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":9,\"amount\":100.00,\"remark\":\"活动补发\",\"createdBy\":999}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.balance").value(100.00));
        }
        verify(f.wallet()).addSpecialFund(eq(9L), argThat(a -> a.compareTo(new BigDecimal("100.00")) == 0),
                eq("MANUAL_GRANT"), isNull(), eq(7L), eq("活动补发"));
        verify(f.benefitLog()).save(any());
    }
}
