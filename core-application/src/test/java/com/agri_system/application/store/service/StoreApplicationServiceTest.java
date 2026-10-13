package com.agri_system.application.store.service;

import com.agri_system.application.store.dto.command.StoreCreateCommand;
import com.agri_system.application.store.exception.StoreErrorCode;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.store.service.StoreDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 门店应用服务单元测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class StoreApplicationServiceTest {
    
    @Mock
    private StoreRepository storeRepository;
    
    @Mock
    private StaffRepository staffRepository;
    
    @Mock
    private StoreDomainService domainService;
    
    @Mock
    private InventoryLocationRepository locationRepository;

    @InjectMocks
    private StoreApplicationService storeService;
    
    private StoreCreateCommand createCommand;
    private Store testStore;
    private Staff testManager;
    
    @BeforeEach
    void setUp() {
        createCommand = new StoreCreateCommand();
        createCommand.setCode("STORE001");
        createCommand.setName("测试门店");
        createCommand.setPhone("13800138000");
        createCommand.setAddress("测试地址");
        createCommand.setSupportPickup(1);
        createCommand.setStatus(1);
        
        testStore = new Store();
        testStore.setId(1L);
        testStore.setCode("STORE001");
        testStore.setName("测试门店");
        testStore.setPhone("13800138000");
        testStore.setStatus(1);
        testStore.setSupportPickup(1);
        testStore.setDeleted(0);
        
        testManager = new Staff();
        testManager.setId(1L);
        testManager.setName("张三");
        testManager.setRole("MANAGER");
        testManager.setStatus(1);
        testManager.setDeleted(0);
    }
    
    @Test
    void testCreateStore_VerifyPhoneAndSupportPickup() {
        // Given: 门店编码不存在
        when(storeRepository.findByCode("STORE001")).thenReturn(Optional.empty());
        doNothing().when(domainService).validateManager(any());
        doAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(1L);
            return null;
        }).when(storeRepository).save(any(Store.class));
        
        // When: 创建门店
        Long storeId = storeService.createStore(createCommand);
        
        // Then: 验证 phone 和 support_pickup 正确入库
        assertNotNull(storeId);
        verify(locationRepository).defaultBackroom(storeId);
        verify(storeRepository, times(1)).save(argThat(store -> {
            Store s = (Store) store;
            return "13800138000".equals(s.getPhone()) && 
                   s.getSupportPickup() != null && 
                   s.getSupportPickup() == 1;
        }));
    }
    
    @Test
    void testCreateStore_ManagerIdNotExists_ShouldThrowException() {
        // Given: 传入不存在的 staffId
        createCommand.setManagerId(999L);
        when(storeRepository.findByCode("STORE001")).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException(StoreErrorCode.MANAGER_NOT_FOUND + ": 指定的店长不存在或已离职"))
                .when(domainService).validateManager(999L);
        
        // When & Then: 应抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            storeService.createStore(createCommand);
        });
        
        assertTrue(exception.getMessage().contains(StoreErrorCode.MANAGER_NOT_FOUND));
    }
    
    @Test
    void testCreateStore_ManagerRoleIsStaff_ShouldThrowException() {
        // Given: 传入角色为 STAFF 的员工
        Staff staff = new Staff();
        staff.setId(2L);
        staff.setRole("STAFF");
        staff.setStatus(1);
        staff.setDeleted(0);
        
        createCommand.setManagerId(2L);
        when(storeRepository.findByCode("STORE001")).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException(StoreErrorCode.MANAGER_INSUFFICIENT_PERMISSION + ": 该员工权限不足（需 MANAGER 或 ADMIN 角色）"))
                .when(domainService).validateManager(2L);
        
        // When & Then: 应抛出 store-1004 异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            storeService.createStore(createCommand);
        });
        
        assertTrue(exception.getMessage().contains(StoreErrorCode.MANAGER_INSUFFICIENT_PERMISSION));
    }
    
    @Test
    void testCreateStore_ManagerIdIsNull_ShouldAllow() {
        // Given: 传入 null -> 允许创建（门店暂无店长）
        createCommand.setManagerId(null);
        when(storeRepository.findByCode("STORE001")).thenReturn(Optional.empty());
        doNothing().when(domainService).validateManager(null);
        doAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(1L);
            return null;
        }).when(storeRepository).save(any(Store.class));
        
        // When: 创建门店
        Long storeId = storeService.createStore(createCommand);
        
        // Then: 应允许创建
        assertNotNull(storeId);
        verify(domainService, times(1)).validateManager(null);
        verify(storeRepository, times(1)).save(argThat(store -> {
            Store s = (Store) store;
            return s.getManagerId() == null;
        }));
    }
}










