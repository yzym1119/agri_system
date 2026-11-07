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
    @Mock private com.agri_system.domain.inventory.repository.InventoryLocationRepository locationRepository;
    @Mock private com.agri_system.domain.inventory.repository.InventoryReceiptBatchRepository receiptBatchRepository;
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

    @Test void 员工身份失效时库存写操作一律拒绝failClosed() {
        // 已登录员工但身份读取失败（被禁用/删除/角色数据异常）→ 不再静默放行
        currentMock = mockStatic(CurrentUser.class);
        currentMock.when(CurrentUser::staffId).thenReturn(8L);
        currentMock.when(CurrentUser::isAdmin).thenThrow(
                cn.dev33.satoken.exception.NotLoginException.newInstance(
                        "STAFF", cn.dev33.satoken.exception.NotLoginException.KICK_OUT, "员工登录已失效", null));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.adjust(adjust(OWN_STORE), 8L));
        assertTrue(failure.getMessage().contains("inventory-2006"), failure.getMessage());
        assertTrue(failure.getMessage().contains("身份已失效"), failure.getMessage());
        verifyNoInteractions(ledger);
    }

    @Test void 普通员工跨店创建货位被拒绝() {
        loginStaff(OWN_STORE, false);
        var command = new com.agri_system.application.inventory.dto.command.InventoryLocationCommand();
        command.setStoreId(OTHER_STORE);
        command.setLocationType("STORE_BACKROOM");
        command.setName("跨店货位");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.createLocation(command));
        assertTrue(failure.getMessage().contains("inventory-2006"));
        verifyNoInteractions(locationRepository);
    }

    @Test void 普通员工移动其他门店批次的货位被拒绝() {
        loginStaff(OWN_STORE, false);
        var batch = new com.agri_system.domain.inventory.model.InventoryReceiptBatch();
        batch.setId(77L); batch.setStoreId(OTHER_STORE); batch.setProductId(5L);
        when(receiptBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        var command = new com.agri_system.application.inventory.dto.command.InventoryMoveCommand();
        command.setBatchId(77L); command.setPurpose("INVENTORY");
        command.setOriginalUnit("袋"); command.setOriginalQuantity(new BigDecimal("1"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.move(command, 8L));
        assertTrue(failure.getMessage().contains("inventory-2006"));
        verifyNoInteractions(ledger);
    }

    @Test void 普通员工本店批次货位移动放行到台账() {
        loginStaff(OWN_STORE, false);
        var batch = new com.agri_system.domain.inventory.model.InventoryReceiptBatch();
        batch.setId(77L); batch.setStoreId(OWN_STORE); batch.setProductId(5L);
        when(receiptBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        // purpose 校验在权限之后：手工移动只允许 INVENTORY/TRANSFER 之外的用途会被拒，
        // 这里用 CHECK 检查权限通过后的行为；用途非法同样会被拒，但错误码不同。
        var command = new com.agri_system.application.inventory.dto.command.InventoryMoveCommand();
        command.setBatchId(77L); command.setPurpose("SALE");
        command.setOriginalUnit("袋"); command.setOriginalQuantity(new BigDecimal("1"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.move(command, 8L));
        // 到达用途校验说明门店归属检查已放行（错误消息不是跨店拒绝）
        assertFalse(failure.getMessage().contains("inventory-2006"), failure.getMessage());
    }
}
