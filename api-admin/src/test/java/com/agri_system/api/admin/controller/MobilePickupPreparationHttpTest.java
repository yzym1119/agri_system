package com.agri_system.api.admin.controller;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.service.MobilePickupPreparationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobilePickupPreparationHttpTest {
    @Test void wholePreparationUsesActualEmployeeStringIdsAndNoCache() throws Exception {
        var service=mock(MobilePickupPreparationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobilePickupPreparationController(service)).build();
        when(service.read(Long.MAX_VALUE,8L)).thenReturn(new MobilePickupPreparationService.Preparation("3","9223372036854775807","O50",null,0,0,List.of()));
        try(var current=mockStatic(CurrentUser.class)){current.when(CurrentUser::staffId).thenReturn(8L);
            mvc.perform(get("/api/admin/mobile/pickup/orders/9223372036854775807?storeId=99&staffId=99")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.orderId").value("9223372036854775807")).andExpect(jsonPath("$.data").doesNotExist());verify(service).read(Long.MAX_VALUE,8L);
        }
    }
}
