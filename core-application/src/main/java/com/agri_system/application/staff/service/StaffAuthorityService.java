package com.agri_system.application.staff.service;

import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.application.auth.util.CurrentUser;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.domain.store.repository.StoreRepository;
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
import org.springframework.transaction.annotation.Isolation;
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
    private final StoreRepository storeRepository;
    private final StaffSessionOperator staffSessionOperator;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long createStaff(StaffCreateCommand command) {
        validateStaffStatus(command.getStatus());
        validateStore(command.getStoreId());
        StpUtil.checkPermission("role:assign");
        roleRepository.lockAdministratorRoles();
        requireEnabledRole(command.getRoleId());
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

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void updateStaff(StaffUpdateCommand command) {
        validateStaffStatus(command.getStatus());
        roleRepository.lockAdministratorRoles();
        Staff staff = staffRepository.findByIdForUpdate(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        validateStore(command.getStoreId());
        boolean wasAdministrator = isActiveAdministrator(staff);
        List<Long> originalRoles = staffRoleRepository.findRoleIdsByStaffId(staff.getId());
        boolean changingRole = command.getRoleId() != null && !originalRoles.contains(command.getRoleId());
        if (changingRole) {
            StpUtil.checkPermission("role:assign");
            requireEnabledRole(command.getRoleId());
        }
        boolean changingStatus = command.getStatus() != null && !command.getStatus().equals(staff.getStatus());
        if (changingStatus) validateStatusChange(staff.getId(), command.getStatus());
        staff.setName(command.getName());
        staff.setPhone(command.getPhone());
        boolean changingStore = !Objects.equals(staff.getStoreId(), command.getStoreId());
        staff.setStoreId(command.getStoreId());
        if (changingStatus) staff.setStatus(command.getStatus());
        staffRepository.save(staff);
        if (changingStore) staffRepository.transferStore(staff.getId(), command.getStoreId());
        if (changingRole) syncStaffRoles(staff.getId(), Collections.singletonList(command.getRoleId()));
        if (wasAdministrator && (changingRole || changingStatus)) ensureAdministratorRemains();
        if (changingStatus && command.getStatus() == 2) staffSessionOperator.kickout("STAFF_" + staff.getId());
    }

    /** PUT 与专用启停入口共享同一权限、本人保护及管理员锁。 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void changeStaffStatus(Long staffId, int status) {
        validateStaffStatus(status);
        roleRepository.lockAdministratorRoles();
        Staff staff = staffRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        validateStatusChange(staffId, status);
        boolean wasAdministrator = isActiveAdministrator(staff);
        staff.setStatus(status);
        staffRepository.save(staff);
        if (wasAdministrator && status == 2) ensureAdministratorRemains();
        if (status == 2) staffSessionOperator.kickout("STAFF_" + staffId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long saveRole(RoleSaveCommand command) {
        if (command.getId() != null) throw new IllegalArgumentException("新增角色不能指定已有ID");
        if (command.getCode() != null) throw new IllegalArgumentException("角色编码由服务端生成");
        validateRoleCommand(command);
        SysRole role = new SysRole();
        role.setName(command.getName());
        role.setCode(codeGenerationService.generateNextCode(CodeGenerationService.CodeType.ROLE));
        role.setStatus(command.getStatus() == null ? 1 : command.getStatus());
        roleRepository.save(role);
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void updateRole(Long id, RoleSaveCommand command) {
        validateRoleCommand(command);
        roleRepository.lockAdministratorRoles();
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        role.setName(command.getName());
        // 角色编码决定管理员身份与数据隔离，编辑时一律保留原编码
        if (command.getStatus() != null) {
            role.setStatus(command.getStatus());
        }
        // 菜单授权只经 POST /{id}/menus（role:assign）维护，编辑角色不改动授权
        roleRepository.save(role);
        if (isAdministratorCode(role.getCode()) && Integer.valueOf(0).equals(command.getStatus())) {
            ensureAdministratorRemains();
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void assignRoleMenus(RoleAssignCommand command) {
        roleRepository.findById(command.getRoleId()).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        roleMenuRepository.deleteByRoleId(command.getRoleId());
        roleMenuRepository.batchInsert(command.getRoleId(), command.getMenuIds());
    }

    public List<SysRole> listRoles() {
        return roleRepository.findAll();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void deleteRole(Long roleId) {
        roleRepository.lockAdministratorRoles();
        // 检查角色是否存在
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        
        // 检查是否有员工使用该角色
        long staffCount = staffRoleRepository.countByRoleId(roleId);
        if (staffCount > 0) {
            throw new IllegalArgumentException("该角色下还有 " + staffCount + " 个员工，无法删除");
        }
        
        roleRepository.deleteById(roleId);
        roleMenuRepository.deleteByRoleId(roleId);
        staffRoleRepository.deleteByRoleId(roleId);
    }

    public List<MenuTreeVO> getMenuTree() {
        return buildMenuTree(menuRepository.findAll());
    }

    public List<MenuTreeVO> getCurrentMenuTree(Long staffId) {
        List<Long> roleIds = enabledRoleIds(staffId);
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
        List<Long> roleIds = enabledRoleIds(staffId);
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
        if (!isActiveStaff(staffId)) return Collections.emptyList();
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

    private boolean isActiveStaff(Long staffId) {
        return staffId != null && staffRepository.findIdentityById(staffId)
                .filter(staff -> staff.isActive() && !staff.hasBeenDeleted()).isPresent();
    }

    private List<Long> enabledRoleIds(Long staffId) {
        if (!isActiveStaff(staffId)) return Collections.emptyList();
        Set<Long> assigned = new HashSet<>(staffRoleRepository.findRoleIdsByStaffId(staffId));
        return roleRepository.findAll().stream().filter(role -> Integer.valueOf(1).equals(role.getStatus()))
                .map(SysRole::getId).filter(assigned::contains).toList();
    }

    private void validateRoleCommand(RoleSaveCommand command) {
        if (command.getStatus() != null && command.getStatus() != 0 && command.getStatus() != 1)
            throw new IllegalArgumentException("角色状态只能为启用或停用");
        if (!CollectionUtils.isEmpty(command.getMenuIds()))
            throw new IllegalArgumentException("菜单授权请使用独立授权入口");
    }

    private void validateStaffStatus(Integer status) {
        if (status != null && status != 1 && status != 2)
            throw new IllegalArgumentException("员工状态只能为在职或离职");
    }

    private void validateStatusChange(Long staffId, int status) {
        StpUtil.checkPermission(status == 2 ? "staff:disable" : "staff:enable");
        if (status == 2 && staffId.equals(CurrentUser.staffId()))
            throw new IllegalArgumentException("不能停用自己的账号，否则将无法登录系统");
    }

    private void validateStore(Long storeId) {
        if (storeId == null) throw new IllegalArgumentException("所属门店不能为空");
        if (!CurrentUser.isAdmin() && !storeId.equals(CurrentUser.storeId()))
            throw new IllegalArgumentException("普通员工只能维护本门店员工，跨店调动须管理员操作");
        var store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("所属门店不存在"));
        if (!store.isEnabled() || store.hasBeenDeleted()) throw new IllegalArgumentException("所属门店已停用");
    }

    private void requireEnabledRole(Long roleId) {
        if (roleId == null || roleRepository.findById(roleId)
                .filter(role -> Integer.valueOf(1).equals(role.getStatus())).isEmpty())
            throw new IllegalArgumentException("目标角色不存在或已停用");
    }

    private boolean isAdministratorCode(String code) {
        return "ADMIN".equals(code) || "SUPER_ADMIN".equals(code);
    }

    private boolean isActiveAdministrator(Staff staff) {
        return staff.isActive() && !staff.hasBeenDeleted()
                && roleRepository.findRoleCodesByStaffId(staff.getId()).stream().anyMatch(this::isAdministratorCode);
    }

    private void ensureAdministratorRemains() {
        if (roleRepository.findActiveAdministratorStaffIdsForUpdate().isEmpty())
            throw new IllegalArgumentException("不能移除最后一个在职有效管理员，否则系统将无法管理");
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
