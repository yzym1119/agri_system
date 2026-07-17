package com.agri_system.application.staff.service;

import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.application.common.service.CodeGenerationApplicationService;
import com.agri_system.application.staff.dto.command.RoleAssignCommand;
import com.agri_system.application.staff.dto.command.RoleSaveCommand;
import com.agri_system.application.staff.dto.command.StaffCreateCommand;
import com.agri_system.application.staff.dto.command.StaffUpdateCommand;
import com.agri_system.application.staff.dto.vo.MenuTreeVO;
import com.agri_system.domain.common.service.CodeGenerationService;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.model.SysMenu;
import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffAuthorityService {

    private final StaffRepository staffRepository;
    private final SysRoleRepository roleRepository;
    private final SysMenuRepository menuRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysStaffRoleRepository staffRoleRepository;
    private final CodeGenerationApplicationService codeGenerationService;

    @Transactional(rollbackFor = Exception.class)
    public Long createStaff(StaffCreateCommand command) {
        // 如果编码为空，自动生成
        String code = command.getCode();
        if (code == null || code.trim().isEmpty()) {
            code = codeGenerationService.generateNextCode(CodeGenerationService.CodeType.STAFF);
        }
        
        Staff staff = new Staff();
        staff.setCode(code);
        staff.setName(command.getName());
        staff.setPhone(command.getPhone());
        staff.setStoreId(command.getStoreId());
        staff.setPassword(BCryptUtils.encode(command.getPassword()));
        staff.setStatus(command.getStatus() == null ? 1 : command.getStatus());
        // 设置默认角色值（数据库字段要求NOT NULL，但实际权限通过sys_staff_role表管理）
        staff.setRole("STAFF"); // 默认值，实际权限由sys_staff_role表控制
        staff.setDeleted(0);
        staffRepository.save(staff);
        syncStaffRoles(staff.getId(), Collections.singletonList(command.getRoleId()));
        return staff.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStaff(StaffUpdateCommand command) {
        Staff staff = staffRepository.findById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        staff.setName(command.getName());
        staff.setPhone(command.getPhone());
        staff.setStoreId(command.getStoreId());
        if (command.getStatus() != null) {
            staff.setStatus(command.getStatus());
        }
        staffRepository.save(staff);
        syncStaffRoles(staff.getId(), Collections.singletonList(command.getRoleId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveRole(RoleSaveCommand command) {
        // 如果编码为空，自动生成
        String code = command.getCode();
        if (code == null || code.trim().isEmpty()) {
            code = codeGenerationService.generateNextCode(CodeGenerationService.CodeType.ROLE);
        }
        
        SysRole role = new SysRole();
        role.setId(command.getId());
        role.setName(command.getName());
        role.setCode(code);
        role.setStatus(command.getStatus());
        roleRepository.save(role);
        if (role.getId() != null && command.getMenuIds() != null) {
            roleMenuRepository.deleteByRoleId(role.getId());
            roleMenuRepository.batchInsert(role.getId(), command.getMenuIds());
        }
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoleMenus(RoleAssignCommand command) {
        roleMenuRepository.deleteByRoleId(command.getRoleId());
        roleMenuRepository.batchInsert(command.getRoleId(), command.getMenuIds());
    }

    public List<SysRole> listRoles() {
        return roleRepository.findAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        // 检查角色是否存在
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        
        // 检查是否有员工使用该角色
        long staffCount = staffRoleRepository.countByRoleId(roleId);
        if (staffCount > 0) {
            throw new IllegalArgumentException("该角色下还有 " + staffCount + " 个员工，无法删除");
        }
        
        // 检查是否是管理员角色（code为ADMIN或SUPER_ADMIN），如果是最后一个管理员角色，禁止删除
        if ("ADMIN".equals(role.getCode()) || "SUPER_ADMIN".equals(role.getCode())) {
            // 检查是否还有其他管理员角色
            List<SysRole> adminRoles = roleRepository.findAll().stream()
                    .filter(r -> "ADMIN".equals(r.getCode()) || "SUPER_ADMIN".equals(r.getCode()))
                    .filter(r -> !r.getId().equals(roleId))
                    .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                    .collect(Collectors.toList());
            if (adminRoles.isEmpty()) {
                throw new IllegalArgumentException("不能删除最后一个管理员角色，否则系统将无法管理");
            }
        }
        
        roleRepository.deleteById(roleId);
        roleMenuRepository.deleteByRoleId(roleId);
        staffRoleRepository.deleteByRoleId(roleId);
    }

    public List<MenuTreeVO> getMenuTree() {
        return buildMenuTree(menuRepository.findAll());
    }

    public List<MenuTreeVO> getCurrentMenuTree(Long staffId) {
        List<Long> roleIds = staffRoleRepository.findRoleIdsByStaffId(staffId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> grantedMenuIds = new LinkedHashSet<>(roleMenuRepository.findMenuIdsByRoleIds(roleIds));
        if (grantedMenuIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysMenu> allMenus = menuRepository.findAll();
        Map<Long, SysMenu> menuMap = allMenus.stream()
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu));

        for (Long menuId : new ArrayList<>(grantedMenuIds)) {
            Long currentId = menuId;
            while (currentId != null && currentId > 0) {
                SysMenu currentMenu = menuMap.get(currentId);
                if (currentMenu == null) {
                    break;
                }
                grantedMenuIds.add(currentId);
                currentId = currentMenu.getParentId();
            }
        }

        List<SysMenu> currentMenus = allMenus.stream()
                .filter(menu -> grantedMenuIds.contains(menu.getId()))
                .collect(Collectors.toList());
        return buildMenuTree(currentMenus);
    }

    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuRepository.findMenuIdsByRoleId(roleId);
    }

    public List<String> loadPermissions(Long staffId) {
        List<Long> roleIds = staffRoleRepository.findRoleIdsByStaffId(staffId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = roleMenuRepository.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return menuRepository.findByIds(menuIds).stream()
                .map(SysMenu::getPermission)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> loadRoleCodes(Long staffId) {
        // 只从关联表查找角色，使用细分的RBAC权限管理
        // 废弃staff.role字段的使用，避免权限冲突
        List<String> roleCodes = new ArrayList<>(roleRepository.findRoleCodesByStaffId(staffId));
        
        // 角色别名映射：SUPER_ADMIN 也视为 ADMIN（用于兼容@SaCheckRole("ADMIN")检查）
            if (roleCodes.contains("SUPER_ADMIN") && !roleCodes.contains("ADMIN")) {
                roleCodes.add("ADMIN");
            }
        // 同样，ADMIN 也视为 SUPER_ADMIN（如果需要）
        if (roleCodes.contains("ADMIN") && !roleCodes.contains("SUPER_ADMIN")) {
            roleCodes.add("SUPER_ADMIN");
        }
        
        return roleCodes;
    }

    private List<MenuTreeVO> buildMenuTree(List<SysMenu> menus) {
        Map<Long, MenuTreeVO> map = new LinkedHashMap<>();
        menus.forEach(menu -> {
            MenuTreeVO vo = new MenuTreeVO();
            vo.setId(menu.getId());
            vo.setParentId(menu.getParentId());
            vo.setName(menu.getName());
            vo.setPermission(menu.getPermission());
            vo.setType(menu.getType());
            vo.setPath(menu.getPath());
            vo.setIcon(menu.getIcon());
            vo.setSortOrder(menu.getSortOrder());
            vo.setVisible(menu.getVisible());
            map.put(vo.getId(), vo);
        });

        List<MenuTreeVO> roots = new ArrayList<>();
        map.values().forEach(node -> {
            if (node.getParentId() == null || node.getParentId() == 0) {
                roots.add(node);
            } else {
                MenuTreeVO parent = map.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        });
        sortMenuTree(roots);
        return roots;
    }

    private void sortMenuTree(List<MenuTreeVO> nodes) {
        nodes.sort(Comparator.comparing(MenuTreeVO::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        nodes.forEach(node -> sortMenuTree(node.getChildren()));
    }

    private void syncStaffRoles(Long staffId, List<Long> roleIds) {
        staffRoleRepository.deleteByStaffId(staffId);
        if (!CollectionUtils.isEmpty(roleIds)) {
            staffRoleRepository.batchInsert(staffId, roleIds);
        }
    }
}
