package com.agri_system.application.staff.service;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.application.performance.dto.StaffDashboardVO;
import com.agri_system.application.performance.service.StaffPerformanceApplicationService;
import com.agri_system.application.staff.dto.command.StaffCreateCommand;
import com.agri_system.application.staff.dto.command.StaffUpdateCommand;
import com.agri_system.application.staff.dto.query.StaffQuery;
import com.agri_system.application.staff.dto.vo.StaffListVO;
import com.agri_system.application.staff.dto.vo.StaffProfileVO;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffLifecycleService {

    private final StaffRepository staffRepository;
    private final StaffAuthorityService authorityService;
    private final StaffPerformanceApplicationService performanceApplicationService;
    private final StaffSessionOperator staffSessionOperator;
    private final StoreRepository storeRepository;
    private final SysRoleRepository roleRepository;
    private final SysStaffRoleRepository staffRoleRepository;

    @Transactional(rollbackFor = Exception.class)
    public Long create(StaffCreateCommand command) {
        return authorityService.createStaff(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(StaffUpdateCommand command) {
        authorityService.updateStaff(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        
        // 检查是否是当前登录用户，禁止停用自己的账号
        if (staffSessionOperator.isLogin()) {
            String loginId = staffSessionOperator.getLoginIdAsString();
            if (loginId.startsWith("STAFF_")) {
                Long currentStaffId = Long.parseLong(loginId.substring("STAFF_".length()));
                if (currentStaffId.equals(staffId)) {
                    throw new IllegalArgumentException("不能停用自己的账号，否则将无法登录系统");
                }
            }
        }
        
        // 检查是否是管理员，如果是最后一个管理员，禁止停用
        List<Long> roleIds = staffRoleRepository.findRoleIdsByStaffId(staffId);
        if (!roleIds.isEmpty()) {
            List<String> roleCodes = roleRepository.findRoleCodesByStaffId(staffId);
            if (roleCodes.contains("ADMIN") || roleCodes.contains("SUPER_ADMIN")) {
                // 检查是否还有其他启用的管理员（通过分页查询所有在职员工）
                Page<Staff> allStaffPage = staffRepository.findPage(null, null, null, 1, 1, Integer.MAX_VALUE);
                long otherAdminCount = allStaffPage.getRecords().stream()
                        .filter(s -> !s.getId().equals(staffId))
                        .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                        .filter(s -> {
                            List<String> codes = roleRepository.findRoleCodesByStaffId(s.getId());
                            return codes.contains("ADMIN") || codes.contains("SUPER_ADMIN");
                        })
                        .count();
                if (otherAdminCount == 0) {
                    throw new IllegalArgumentException("不能停用最后一个管理员账号，否则系统将无法管理");
                }
            }
        }
        
        staff.setStatus(2);
        staffRepository.save(staff);
        staffSessionOperator.kickout("STAFF_" + staffId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        staff.setStatus(1);
        staffRepository.save(staff);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long staffId, String password) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        staff.setPassword(BCryptUtils.encode(password));
        staffRepository.save(staff);
        staffSessionOperator.kickout("STAFF_" + staffId);
    }

    public StaffProfileVO profile(Long staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        List<Long> roleIds = staffRoleRepository.findRoleIdsByStaffId(staffId);
        List<String> roles = authorityService.loadRoleCodes(staffId);
        StaffDashboardVO dashboard = performanceApplicationService.dashboard(staffId);
        StaffProfileVO vo = new StaffProfileVO();
        vo.setId(staff.getId());
        vo.setCode(staff.getCode());
        vo.setName(staff.getName());
        vo.setPhone(staff.getPhone());
        vo.setStoreId(staff.getStoreId());
        vo.setStatus(staff.getStatus());
        vo.setRoles(roles);
        vo.setRoleIds(roleIds);
        vo.setPerformance(dashboard);
        return vo;
    }
    
    /**
     * 分页查询员工列表
     */
    public Page<StaffListVO> queryStaffList(StaffQuery query) {
        Page<Staff> staffPage = staffRepository.findPage(
                query.getCode(),
                query.getName(),
                query.getStoreId(),
                query.getStatus(),
                query.getPageNum(),
                query.getPageSize()
        );
        
        // 转换为VO
        List<StaffListVO> voList = staffPage.getRecords().stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());
        
        // 填充门店名称和角色信息
        // 获取所有门店ID
        List<Long> storeIds = voList.stream()
                .map(StaffListVO::getStoreId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        Map<Long, String> storeNameMap = storeIds.stream()
                .map(storeRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(
                        store -> store.getId(),
                        store -> store.getName(),
                        (v1, v2) -> v1
                ));
        
        Map<Long, SysRole> roleMap = roleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        role -> role.getId(),
                        role -> role,
                        (v1, v2) -> v1
                ));
        
        voList.forEach(vo -> {
            // 填充门店名称
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNameMap.get(vo.getStoreId()));
            }
            
            // 填充角色信息
            List<Long> roleIds = staffRoleRepository.findRoleIdsByStaffId(vo.getId());
            List<StaffListVO.RoleInfo> roles = roleIds.stream()
                    .map(roleMap::get)
                    .filter(role -> role != null)
                    .map(role -> {
                        StaffListVO.RoleInfo roleInfo = new StaffListVO.RoleInfo();
                        roleInfo.setId(role.getId());
                        roleInfo.setName(role.getName());
                        roleInfo.setCode(role.getCode());
                        return roleInfo;
                    })
                    .collect(Collectors.toList());
            vo.setRoles(roles);
        });
        
        Page<StaffListVO> voPage = new Page<>();
        voPage.setPageNumber(staffPage.getPageNumber());
        voPage.setPageSize(staffPage.getPageSize());
        voPage.setTotalRow(staffPage.getTotalRow());
        voPage.setRecords(voList);
        
        return voPage;
    }
    
    private StaffListVO convertToListVO(Staff staff) {
        StaffListVO vo = new StaffListVO();
        vo.setId(staff.getId());
        vo.setCode(staff.getCode());
        vo.setName(staff.getName());
        vo.setPhone(staff.getPhone());
        vo.setStoreId(staff.getStoreId());
        vo.setStatus(staff.getStatus());
        return vo;
    }
}



