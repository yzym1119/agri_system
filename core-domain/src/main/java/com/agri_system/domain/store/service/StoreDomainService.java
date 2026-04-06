package com.agri_system.domain.store.service;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 门店领域服务
 * 负责门店相关的业务规则校验
 * 
 * @author AI Generated
 */
@RequiredArgsConstructor
public class StoreDomainService {
    
    private final StaffRepository staffRepository;
    private final SysRoleRepository roleRepository;
    private final SysStaffRoleRepository staffRoleRepository;
    private final SysMenuRepository menuRepository;
    
    /**
     * 校验店长
     * 规则：
     * 1. 员工必须存在且 deleted=0
     * 2. 员工 status=1 (在职)
     * 3. 员工必须拥有 store:manager 权限（从sys_staff_role表检查）
     * 
     * @param managerId 店长ID，可为null（表示门店暂无店长）
     * @throws IllegalArgumentException 如果店长校验失败
     */
    public void validateManager(Long managerId) {
        // 如果 managerId 为 null，允许创建（门店暂无店长）
        if (managerId == null) {
            return;
        }
        
        // 查找员工
        Staff staff = staffRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "store-1003: 指定的店长不存在或已离职"));
        
        // 校验员工是否已删除
        if (staff.hasBeenDeleted()) {
            throw new IllegalArgumentException(
                    "store-1003: 指定的店长不存在或已离职");
        }
        
        // 校验员工是否在职
        if (!staff.isActive()) {
            throw new IllegalArgumentException(
                    "store-1003: 指定的店长不存在或已离职");
        }
        
        // 检查员工是否拥有店长权限（store:manager）
        // 通过检查员工角色是否拥有 store:manager 权限来判断
        List<Long> roleIds = staffRoleRepository.findRoleIdsByStaffId(managerId);
        if (roleIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "store-1004: 该员工权限不足（需要店长权限 store:manager）");
        }
        
        // 检查角色是否拥有 store:manager 权限
        boolean hasManagerPermission = menuRepository.hasPermission(roleIds, "store:manager");
        if (!hasManagerPermission) {
            throw new IllegalArgumentException(
                    "store-1004: 该员工权限不足（需要店长权限 store:manager）");
        }
    }
    
    /**
     * 校验门店状态
     * 如果门店已停用，禁止某些操作
     * 
     * @param store 门店实体
     * @throws IllegalArgumentException 如果门店已停用
     */
    public void validateStoreEnabled(Store store) {
        if (!store.isEnabled()) {
            throw new IllegalArgumentException(
                    "store-1005: 门店已停用，禁止操作");
        }
    }
}

