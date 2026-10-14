package com.agri_system.application.credit.service;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 员工账款操作的门店范围。身份由登录员工解析，不能借请求体变更操作者。 */
@Service
@RequiredArgsConstructor
public class CreditAccessService {
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final SysRoleRepository roleRepository;

    public Scope resolve(Long operatorId, Long requestedStoreId) {
        if (operatorId == null) throw new IllegalArgumentException("账款操作需要员工登录");
        Staff staff = staffRepository.findById(operatorId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
        if (!staff.isActive() || staff.hasBeenDeleted()) throw new IllegalArgumentException("当前员工已停用或离职");
        boolean admin = roleRepository.findRoleCodesByStaffId(operatorId).stream()
                .anyMatch(code -> "ADMIN".equals(code) || "SUPER_ADMIN".equals(code));
        if (!admin && requestedStoreId != null && !requestedStoreId.equals(staff.getStoreId())) {
            throw new IllegalArgumentException("不能处理其他门店账款");
        }
        Long storeId = requestedStoreId == null ? staff.getStoreId() : requestedStoreId;
        if (storeId == null) throw new IllegalArgumentException("当前员工未配置收款门店");
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("门店不存在"));
        if (!Integer.valueOf(1).equals(store.getStatus())) throw new IllegalArgumentException("门店已停用");
        return new Scope(operatorId, storeId, admin);
    }

    public record Scope(Long operatorId, Long storeId, boolean admin) {}
}
