package com.agri_system.application.store.service;

import com.agri_system.application.common.service.CodeGenerationApplicationService;
import com.agri_system.application.store.dto.command.StoreCreateCommand;
import com.agri_system.application.store.dto.command.StoreUpdateCommand;
import com.agri_system.application.store.dto.query.StoreQuery;
import com.agri_system.application.store.dto.vo.StoreDetailVO;
import com.agri_system.application.store.exception.StoreErrorCode;
import com.agri_system.domain.common.service.CodeGenerationService;
import com.agri_system.domain.store.model.Store;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.store.service.StoreDomainService;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 门店应用服务
 * 负责业务流程编排，不包含业务规则
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class StoreApplicationService {
    
    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;
    private final StoreDomainService domainService;
    private final CodeGenerationApplicationService codeGenerationService;
    
    /**
     * 创建门店
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createStore(StoreCreateCommand command) {
        // 如果编码为空，自动生成
        String code = command.getCode();
        if (code == null || code.trim().isEmpty()) {
            code = codeGenerationService.generateNextCode(CodeGenerationService.CodeType.STORE);
        }
        
        // 校验编码唯一性
        storeRepository.findByCode(code)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            StoreErrorCode.STORE_CODE_EXISTS + ": 门店编码已存在");
                });
        
        // 校验店长
        domainService.validateManager(command.getManagerId());
        
        // 创建门店实体
        Store store = new Store();
        store.setCode(code);
        store.setName(command.getName());
        store.setPhone(command.getPhone());
        store.setAddress(command.getAddress());
        store.setManagerId(command.getManagerId());
        store.setStatus(command.getStatus() != null ? command.getStatus() : 1);
        store.setSupportPickup(command.getSupportPickup() != null ? command.getSupportPickup() : 1);
        store.setRemark(command.getRemark());
        store.setCreatedAt(LocalDateTime.now());
        store.setUpdatedAt(LocalDateTime.now());
        store.setDeleted(0);
        
        storeRepository.save(store);
        return store.getId();
    }
    
    /**
     * 更新门店
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStore(StoreUpdateCommand command) {
        Store store = storeRepository.findById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        StoreErrorCode.STORE_NOT_FOUND + ": 门店不存在"));
        
        // 校验店长
        domainService.validateManager(command.getManagerId());
        
        // 更新门店信息（注意：code 不可修改）
        store.setName(command.getName());
        store.setPhone(command.getPhone());
        store.setAddress(command.getAddress());
        store.setManagerId(command.getManagerId());
        if (command.getStatus() != null) {
            store.setStatus(command.getStatus());
        }
        if (command.getSupportPickup() != null) {
            store.setSupportPickup(command.getSupportPickup());
        }
        store.setRemark(command.getRemark());
        store.setUpdatedAt(LocalDateTime.now());
        
        storeRepository.save(store);
    }
    
    /**
     * 查询门店详情
     */
    public StoreDetailVO getStoreDetail(Long id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        StoreErrorCode.STORE_NOT_FOUND + ": 门店不存在"));
        
        StoreDetailVO vo = convertToDetailVO(store);
        
        // 填充分类名称
        if (store.getManagerId() != null) {
            staffRepository.findById(store.getManagerId())
                    .ifPresent(staff -> vo.setManagerName(staff.getName()));
        }
        
        return vo;
    }
    
    /**
     * 分页查询门店
     */
    public Page<StoreDetailVO> queryStores(StoreQuery query) {
        Page<Store> storePage = storeRepository.findPage(
                query.getCode(),
                query.getName(),
                query.getStatus(),
                query.getSupportPickup(),
                query.getPageNum(),
                query.getPageSize()
        );
        
        // 转换为VO
        List<StoreDetailVO> voList = storePage.getRecords().stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
        
        // 填充分类名称
        voList.forEach(vo -> {
            if (vo.getManagerId() != null) {
                staffRepository.findById(vo.getManagerId())
                        .ifPresent(staff -> vo.setManagerName(staff.getName()));
            }
        });
        
        Page<StoreDetailVO> voPage = new Page<>();
        voPage.setPageNumber(storePage.getPageNumber());
        voPage.setPageSize(storePage.getPageSize());
        voPage.setTotalRow(storePage.getTotalRow());
        voPage.setRecords(voList);
        
        return voPage;
    }
    
    /**
     * 删除门店（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStore(Long id) {
        // 校验门店是否存在
        storeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        StoreErrorCode.STORE_NOT_FOUND + ": 门店不存在"));
        
        // 逻辑删除
        storeRepository.delete(id);
    }
    
    // ==================== 私有方法：转换 ====================
    
    private StoreDetailVO convertToDetailVO(Store store) {
        StoreDetailVO vo = new StoreDetailVO();
        vo.setId(store.getId());
        vo.setCode(store.getCode());
        vo.setName(store.getName());
        vo.setPhone(store.getPhone());
        vo.setAddress(store.getAddress());
        vo.setManagerId(store.getManagerId());
        vo.setStatus(store.getStatus());
        vo.setSupportPickup(store.getSupportPickup());
        vo.setRemark(store.getRemark());
        vo.setCreatedAt(store.getCreatedAt());
        vo.setUpdatedAt(store.getUpdatedAt());
        vo.setCreatedBy(store.getCreatedBy());
        vo.setUpdatedBy(store.getUpdatedBy());
        return vo;
    }
}

