package com.agri_system.application.store.service;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessStoreOptionsTest {
    @Test void ordinaryEmployeeGetsOnlyCurrentStoreIdAndName() {
        StoreRepository repository=mock(StoreRepository.class);
        var service=new StoreApplicationService(repository,null,null,null,null,null);
        var store=new Store(); store.setId(1L); store.setName("本店"); store.setDeleted(0);store.setStatus(1);
        store.setPhone("不能通过业务下拉泄漏");when(repository.findById(1L)).thenReturn(Optional.of(store));
        try(var user=new com.agri_system.application.support.EmployeeIdentityTestScope(9L,1L,false)) {
            var result=service.businessOptions();assertEquals(1,result.size());
            assertEquals(1L,result.get(0).id());assertEquals("本店",result.get(0).name());
            assertEquals(2,result.get(0).getClass().getRecordComponents().length);
            verify(repository,never()).findPage(any(),any(),any(),any(),anyInt(),anyInt());
        }
    }

    @Test void missingIdentityDoesNotFallBackToAllStores() {
        StoreRepository repository=mock(StoreRepository.class);
        var service=new StoreApplicationService(repository,null,null,null,null,null);
        try(var user=new com.agri_system.application.support.EmployeeIdentityTestScope(null,null,false)) {
            assertThrows(IllegalArgumentException.class,service::businessOptions);verifyNoInteractions(repository);
        }
    }
}
