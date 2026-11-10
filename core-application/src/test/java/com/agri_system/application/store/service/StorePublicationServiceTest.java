package com.agri_system.application.store.service;

import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorePublicationServiceTest {
    final StorePublicationRepository rows=mock(StorePublicationRepository.class);final StaffRepository staff=mock(StaffRepository.class);final SysRoleRepository roles=mock(SysRoleRepository.class);final SysOperationLogRepository logs=mock(SysOperationLogRepository.class);
    final StorePublicationService service=new StorePublicationService(rows,staff,roles,logs);
    StorePublicationRepository.State state(int visible,long version){return new StorePublicationRepository.State(2L,"门店","13800000000","实际地址",1,0,visible,version);}
    void actor(boolean admin){var a=new Staff();a.setId(9L);a.setStoreId(admin?1L:2L);a.setStatus(1);a.setDeleted(0);when(staff.findIdentityById(9L)).thenReturn(Optional.of(a));when(roles.findRoleCodesByStaffId(9L)).thenReturn(admin?List.of("ADMIN"):List.of("CASHIER"));}
    @Test void publicationUsesLockedRealPreviewAndRecordsActorWithoutPrivateStoreFields(){
        actor(false);var before=state(0,1);when(rows.find(2L,false)).thenReturn(Optional.of(before));when(rows.find(2L,true)).thenReturn(Optional.of(before));when(rows.setVisible(2L,1L,1)).thenReturn(true);
        var preview=service.preview(9L,2L);var result=service.setVisible(9L,2L,true,"1",preview.fingerprint());assertTrue(result.visible());assertEquals("2",result.version());verify(rows).find(2L,true);verify(logs).save(argThat(log->log.getStaffId().equals(9L)&&"门店公开".equals(log.getModule())&&!log.getRequestParams().contains("13800000000")));
    }
    @Test void staleVersionOrChangedContentDoesNotPublishOrLog(){
        actor(true);when(rows.find(2L,true)).thenReturn(Optional.of(state(0,2)));assertThrows(IllegalArgumentException.class,()->service.setVisible(9L,2L,true,"1","0".repeat(64)));verify(rows,never()).setVisible(anyLong(),anyLong(),anyInt());verifyNoInteractions(logs);
    }
    @Test void ordinaryEmployeesCannotPublishAnotherStoreAndDisabledEmployeesAreRejected(){
        actor(false);assertThrows(IllegalArgumentException.class,()->service.preview(9L,1L));verifyNoInteractions(rows);
        when(staff.findIdentityById(9L)).thenReturn(Optional.empty());assertThrows(IllegalArgumentException.class,()->service.preview(9L,2L));
    }
    @Test void disabledStoreCannotBePublishedButCanBeWithdrawn(){
        actor(true);var disabled=new StorePublicationRepository.State(2L,"店","13800000000","地址",2,0,1,1L);when(rows.find(2L,false)).thenReturn(Optional.of(disabled));when(rows.find(2L,true)).thenReturn(Optional.of(disabled));when(rows.setVisible(2L,1L,0)).thenReturn(true);
        var preview=service.preview(9L,2L);assertFalse(preview.canPublish());assertThrows(IllegalArgumentException.class,()->service.setVisible(9L,2L,true,"1",preview.fingerprint()));assertFalse(service.setVisible(9L,2L,false,"1",preview.fingerprint()).visible());
    }
}
