package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.inventory.service.MobileScanQueryService;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobileScanHttpTest {
    @Test void onlyCurrentEmployeeIsPassedAndRawContractPreservesStringIds() throws Exception {
        var query=mock(MobileScanQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobileScanController(query)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(query.resolve("P1","INVENTORY_LOOKUP",7L)).thenReturn(new MobileScanQueryService.Resolution("9223372036854775807","UNKNOWN",List.of(),false));
        try(var current=mockStatic(CurrentUser.class)) {
            current.when(CurrentUser::staffId).thenReturn(7L);
            mvc.perform(post("/api/admin/mobile/scan/resolve").contentType("application/json")
                    .content("{\"code\":\"P1\",\"purpose\":\"INVENTORY_LOOKUP\",\"staffId\":999,\"storeId\":999}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value("9223372036854775807"))
                    .andExpect(jsonPath("$.type").value("UNKNOWN")).andExpect(jsonPath("$.data").doesNotExist());
            verify(query).resolve("P1","INVENTORY_LOOKUP",7L);
            mvc.perform(get("/api/admin/mobile/inventory/11").param("storeId","999").param("batchId","12").param("locationId","13").param("page","2")).andExpect(status().isOk());
            verify(query).inventory(11L,12L,13L,2,7L);
        }
    }
    @Test void malformedRequestsDoNotReachQueries() throws Exception {
        var query=mock(MobileScanQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new MobileScanController(query)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/admin/mobile/scan/resolve").contentType("application/json").content("{\"code\":\"\",\"purpose\":\"INVENTORY_LOOKUP\"}"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(query);
    }
}
