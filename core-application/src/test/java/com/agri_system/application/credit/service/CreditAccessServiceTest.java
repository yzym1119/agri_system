package com.agri_system.application.credit.service;
import com.agri_system.domain.store.model.*;
import com.agri_system.domain.store.repository.*;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class CreditAccessServiceTest {
    @Mock StaffRepository staffRepository;@Mock StoreRepository storeRepository;@Mock SysRoleRepository roleRepository;
    @InjectMocks CreditAccessService service;
    Staff staff;
    @BeforeEach void setup(){
        staff=new Staff();staff.setId(8L);staff.setStoreId(1L);staff.setStatus(1);staff.setDeleted(0);
        when(staffRepository.findById(8L)).thenReturn(Optional.of(staff));
        when(roleRepository.findRoleCodesByStaffId(8L)).thenReturn(List.of("CASHIER"));
        for(long id:List.of(1L,2L)){Store s=new Store();s.setId(id);s.setStatus(1);when(storeRepository.findById(id)).thenReturn(Optional.of(s));}
    }
    @Test void ordinaryStaffDefaultsToOwnStore(){assertEquals(1L,service.resolve(8L,null).storeId());}
    @Test void ordinaryStaffCannotSelectAnotherStore(){assertThrows(IllegalArgumentException.class,()->service.resolve(8L,2L));verify(storeRepository,never()).findById(2L);}
    @Test void databaseAdminRoleCanChooseStore(){when(roleRepository.findRoleCodesByStaffId(8L)).thenReturn(List.of("ADMIN"));assertEquals(2L,service.resolve(8L,2L).storeId());}
    @Test void oldStaffRoleFieldCannotInventAdminAuthority(){staff.setRole("ADMIN");assertThrows(IllegalArgumentException.class,()->service.resolve(8L,2L));}
    @Test void missingOrDepartedStaffAndDisabledStoreAreRejected(){assertThrows(IllegalArgumentException.class,()->service.resolve(null,1L));staff.setStatus(2);assertThrows(IllegalArgumentException.class,()->service.resolve(8L,1L));staff.setStatus(1);Store disabled=new Store();disabled.setStatus(2);when(storeRepository.findById(1L)).thenReturn(Optional.of(disabled));assertThrows(IllegalArgumentException.class,()->service.resolve(8L,1L));}
}
