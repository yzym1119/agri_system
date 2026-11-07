package com.agri_system.application.inventory.service;

import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.command.InventoryTransferCommand;
import com.agri_system.domain.inventory.repository.InventoryBatchRepository;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.inventory.repository.InventoryTransferOrderDetailRepository;
import com.agri_system.domain.inventory.repository.InventoryTransferOrderRepository;
import com.agri_system.domain.inventory.service.InventoryDomainService;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 跨门店库存写入边界（评审 #5）：普通员工只能操作本店库存，
 * 请求体中的 storeId 指向其他门店时必须被拒绝；管理员不受限。
 */
@ExtendWith(MockitoExtension.class)
class InventoryCrossStoreBoundaryTest {
    @Mock private InventoryLedgerService ledger;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryTransferOrderRepository transferOrderRepository;
    @Mock private InventoryTransferOrderDetailRepository transferOrderDetailRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private InventoryApplicationService service;

    private static final Long OWN_STORE = 3L;
    private static final Long OTHER_STORE = 9L;
    private MockedStatic<CurrentUser> currentMock;

    @AfterEach void tearDown() { if (currentMock != null) { currentMock.close(); currentMock = null; } }

    private void loginStaff(Long storeId, boolean admin) {
        currentMock = mockStatic(CurrentUser.class);
        currentMock.when(CurrentUser::staffId).thenReturn(8L);
        currentMock.when(CurrentUser::isAdmin).thenReturn(admin);
        if (storeId != null) currentMock.when(CurrentUser::storeId).thenReturn(storeId);
    }

    private InventoryAdjustCommand adjust(Long storeId) {
        InventoryAdjustCommand command = new InventoryAdjustCommand();
        command.setStoreId(storeId); command.setProductId(5L);
        command.setQuantityDiff(new BigDecimal("1")); command.setExpectedQuantity(BigDecimal.ZERO);
        command.setType(1);
        return command;
    }

    @Test void 普通员工跨店盘点被拒绝且不触达台账() {
        loginStaff(OWN_STORE, false);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.adjust(adjust(OTHER_STORE), 8L));
        assertTrue(failure.getMessage().contains("inventory-2006"), "应返回跨店拒绝错误码: " + failure.getMessage());
        verifyNoInteractions(ledger);
    }

    @Test void 普通员工本店盘点正常执行() {
        loginStaff(OWN_STORE, false);
        service.adjust(adjust(OWN_STORE), 8L);
        verify(ledger).adjust(any(InventoryAdjustCommand.class), eq(8L));
    }

    @Test void 普通员工跨店入库与出库均被拒绝() {
        loginStaff(OWN_STORE, false);
        InventoryInCommand in = new InventoryInCommand();
        in.setStoreId(OTHER_STORE); in.setProductId(5L); in.setQuantity(new BigDecimal("10"));
        InventoryOutCommand out = new InventoryOutCommand();
        out.setStoreId(OTHER_STORE); out.setProductId(5L); out.setQuantity(new BigDecimal("1"));
        assertThrows(IllegalArgumentException.class, () -> service.inbound(in, 8L));
        assertThrows(IllegalArgumentException.class, () -> service.outbound(out, 8L));
        verifyNoInteractions(ledger);
    }

    @Test void 普通员工从其他门店发起调拨被拒绝() {
        loginStaff(OWN_STORE, false);
        InventoryTransferCommand command = new InventoryTransferCommand();
        command.setFromStoreId(OTHER_STORE); command.setToStoreId(OWN_STORE);
        command.setProductId(5L); command.setQuantity(new BigDecimal("2")); command.setRequestKey("t-1");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(command, 8L));
        assertTrue(failure.getMessage().contains("inventory-2006"));
        verifyNoInteractions(storeRepository, ledger);
    }

    @Test void 未分配门店的非管理员员工禁止盘点() {
        loginStaff(null, false);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.adjust(adjust(OTHER_STORE), 8L));
        assertTrue(failure.getMessage().contains("inventory-2006"));
        verifyNoInteractions(ledger);
    }

    @Test void 管理员可以操作任意门店库存() {
        loginStaff(null, true);
        service.adjust(adjust(OTHER_STORE), 8L);
        verify(ledger).adjust(any(InventoryAdjustCommand.class), eq(8L));
    }
}
