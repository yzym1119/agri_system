package com.agri_system.api.admin.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.order.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class PosSubmissionHttpTest {
 @Test void recoveryAlwaysUsesCurrentEmployeeAndDoesNotAcceptOwnerFromClient() throws Exception {
  var submissions=mock(PosSaleSubmissionService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new OrderAdminController(mock(OrderApplicationService.class),mock(PickupFulfillmentService.class),submissions)).build();
  String key="17930923-8ed7-4da0-99ca-47a938860de3";
  when(submissions.lookup(key,7L)).thenReturn(new PosSaleSubmissionService.Result(key,"UNKNOWN",null));
  when(submissions.cancel(key,7L)).thenReturn(new PosSaleSubmissionService.Result(key,"CANCELLED",null));
  try(var auth=mockStatic(StpUtil.class)) {
   auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
   mvc.perform(get("/api/admin/orders/pos/submissions/"+key).param("staffId","999")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNKNOWN"));
   mvc.perform(post("/api/admin/orders/pos/submissions/"+key+"/cancel")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
   verify(submissions).lookup(key,7L);verify(submissions).cancel(key,7L);
  }
 }
 @Test void walletSubmissionReachesDurableCoordinatorBeforeAnyEphemeralCodeLock() throws Exception {
  var submissions=mock(PosSaleSubmissionService.class);
  when(submissions.submit(anyString(),any(),eq(7L))).thenReturn(42L);
  var mvc=MockMvcBuilders.standaloneSetup(new OrderAdminController(mock(OrderApplicationService.class),mock(PickupFulfillmentService.class),submissions)).setControllerAdvice(new com.agri_system.infrastructure.common.exception.GlobalExceptionHandler()).build();
  try(var auth=mockStatic(StpUtil.class)) {
   auth.when(StpUtil::getLoginIdAsString).thenReturn("STAFF_7");
   mvc.perform(post("/api/admin/orders/pos").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
    .content("{\"requestKey\":\"17930923-8ed7-4da0-99ca-47a938860de3\",\"storeId\":1,\"staffId\":7,\"paymentType\":4,\"walletAuthCode\":\"TEST-CODE\",\"items\":[{\"productId\":1,\"quantity\":1,\"unit\":\"袋\"}]}"))
    .andExpect(status().isOk()).andExpect(content().string("42"));
   verify(submissions).submit(anyString(),any(),eq(7L));
  }
 }
}
