package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.dto.vo.MobileOwnOrderVO;
import com.agri_system.application.order.service.MobileOwnOrderService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobileOwnOrderHttpTest {
    @Test void actualEmployeeAndDateBindingIgnoreClientScopeAndDoNotCache() throws Exception {
        var service=mock(MobileOwnOrderService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobileOwnOrderController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(service.list(eq(8L),isNull(),any(),any(),eq(6))).thenReturn(new MobileOwnOrderVO.Orders("3",List.of(),null));
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(8L);
            mvc.perform(get("/api/admin/mobile/orders?staffId=99&storeId=4&from=2026-11-09&to=2026-11-09&state=6"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.storeId").value("3")).andExpect(jsonPath("$.data").doesNotExist());
            verify(service).list(8L,null,LocalDate.of(2026,11,9),LocalDate.of(2026,11,9),6);
            mvc.perform(get("/api/admin/mobile/orders?from=2026-02-30")).andExpect(jsonPath("$.code").value(10001));
        }
    }
    @Test void maximumLongIsNotConvertedThroughDouble() throws Exception {
        var service=mock(MobileOwnOrderService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobileOwnOrderController(service)).build();
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(8L);
            when(service.detail(8L,Long.MAX_VALUE)).thenReturn(new MobileOwnOrderVO.Detail("3",null,List.of()));
            mvc.perform(get("/api/admin/mobile/orders/9223372036854775807")).andExpect(status().isOk());
            verify(service).detail(8L,Long.MAX_VALUE);
        }
    }
}
