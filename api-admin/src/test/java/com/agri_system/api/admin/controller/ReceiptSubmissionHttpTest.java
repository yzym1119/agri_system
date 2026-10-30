package com.agri_system.api.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.purchase.service.PurchaseApplicationService;
import com.agri_system.application.purchase.dto.vo.ReceiptSubmissionVO;
import com.agri_system.application.purchase.dto.vo.MobileReceivingVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReceiptSubmissionHttpTest {
    @Test void originalRequestAndMobileReadUseCurrentEmployeeNotClientOwner() throws Exception {
        var query=mock(PurchaseApplicationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new PurchaseController(query)).build();
        when(query.getReceiptSubmission("original-key",7L)).thenReturn(new ReceiptSubmissionVO("original-key","UNKNOWN","1",null,null,null,List.of()));
        when(query.mobileReceivingPage(1,"",7L)).thenReturn(new MobileReceivingVO.Orders("1",1,false,List.of()));
        try(var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
            mvc.perform(get("/api/admin/purchase/receipts/submissions/original-key").param("staffId","99").param("storeId","2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNKNOWN")).andExpect(jsonPath("$.storeId").value("1"));
            mvc.perform(get("/api/admin/purchase/receiving/mobile/orders").param("storeId","2")).andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value("1"));
            verify(query).getReceiptSubmission("original-key",7L);verify(query).mobileReceivingPage(1,"",7L);
        }
    }
}
