package com.agri_system.application.staff.service;

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

    public Long create(StaffCreateCommand command) {
        return authorityService.createStaff(command);
    }

    public void update(StaffUpdateCommand command) {
        authorityService.updateStaff(command);
    }

    public void disable(Long staffId) {
        authorityService.changeStaffStatus(staffId, 2);
    }

    public void enable(Long staffId) {
        authorityService.changeStaffStatus(staffId, 1);
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



