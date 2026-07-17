package com.agri_system.application.purchase.service;

import com.agri_system.application.common.service.CodeGenerationApplicationService;
import com.agri_system.application.purchase.dto.command.SupplierCreateCommand;
import com.agri_system.application.purchase.dto.command.SupplierUpdateCommand;
import com.agri_system.application.purchase.dto.query.SupplierQuery;
import com.agri_system.application.purchase.dto.vo.SupplierDetailVO;
import com.agri_system.domain.common.service.CodeGenerationService;
import com.agri_system.domain.purchase.repository.PurchaseRepository;
import com.agri_system.domain.purchase.model.Supplier;
import com.agri_system.domain.purchase.repository.SupplierRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 供应商应用服务
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class SupplierApplicationService {
    
    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final CodeGenerationApplicationService codeGenerationService;
    
    /**
     * 创建供应商
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSupplier(SupplierCreateCommand command) {
        // 如果编码为空，自动生成
        final String code = (command.getCode() == null || command.getCode().trim().isEmpty())
                ? codeGenerationService.generateNextCode(CodeGenerationService.CodeType.SUPPLIER)
                : command.getCode();
        
        // 校验编码唯一性
        supplierRepository.findByCode(code)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("供应商编码已存在: " + code);
                });
        
        // 创建供应商实体
        Supplier supplier = new Supplier();
        supplier.setCode(code);
        supplier.setName(command.getName());
        supplier.setContactName(command.getContactName());
        supplier.setContactPhone(command.getContactPhone());
        supplier.setBankAccount(command.getBankAccount());
        supplier.setBankName(command.getBankName());
        supplier.setAddress(command.getAddress());
        supplier.setStatus(command.getStatus() != null ? command.getStatus() : 1);
        supplier.setRemark(command.getRemark());
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        // 注意：supplier表没有deleted字段，不使用逻辑删除
        
        supplierRepository.save(supplier);
        return supplier.getId();
    }
    
    /**
     * 更新供应商
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSupplier(SupplierUpdateCommand command) {
        Supplier supplier = supplierRepository.findById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        
        supplier.setName(command.getName());
        supplier.setContactName(command.getContactName());
        supplier.setContactPhone(command.getContactPhone());
        supplier.setBankAccount(command.getBankAccount());
        supplier.setBankName(command.getBankName());
        supplier.setAddress(command.getAddress());
        if (command.getStatus() != null) {
            supplier.setStatus(command.getStatus());
        }
        supplier.setRemark(command.getRemark());
        supplier.setUpdatedAt(LocalDateTime.now());
        
        supplierRepository.save(supplier);
    }
    
    /**
     * 查询供应商详情
     */
    public SupplierDetailVO getSupplierDetail(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        return convertToVO(supplier);
    }
    
    /**
     * 分页查询供应商
     */
    public Page<SupplierDetailVO> querySuppliers(SupplierQuery query) {
        Page<Supplier> page = supplierRepository.findPage(
                query.getCode(),
                query.getName(),
                query.getStatus(),
                query.getPageNum() != null ? query.getPageNum() : 1,
                query.getPageSize() != null ? query.getPageSize() : 10
        );
        
        // 转换为VO
        Page<SupplierDetailVO> voPage = new Page<>();
        voPage.setPageNumber(page.getPageNumber());
        voPage.setPageSize(page.getPageSize());
        voPage.setTotalRow(page.getTotalRow());
        voPage.setRecords(page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()));
        
        return voPage;
    }
    
    /**
     * 删除供应商
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSupplier(Long id) {
        // 检查供应商是否存在
        supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在"));

        long purchaseCount = purchaseRepository.countBySupplierId(id);
        if (purchaseCount > 0) {
            throw new IllegalArgumentException("该供应商已被采购单引用，不能删除");
        }

        supplierRepository.delete(id);
    }
    
    /**
     * 转换为VO
     */
    private SupplierDetailVO convertToVO(Supplier supplier) {
        SupplierDetailVO vo = new SupplierDetailVO();
        vo.setId(supplier.getId());
        vo.setCode(supplier.getCode());
        vo.setName(supplier.getName());
        vo.setContactName(supplier.getContactName());
        vo.setContactPhone(supplier.getContactPhone());
        vo.setBankAccount(supplier.getBankAccount());
        vo.setBankName(supplier.getBankName());
        vo.setAddress(supplier.getAddress());
        vo.setStatus(supplier.getStatus());
        vo.setRemark(supplier.getRemark());
        vo.setCreatedAt(supplier.getCreatedAt());
        vo.setUpdatedAt(supplier.getUpdatedAt());
        return vo;
    }
}
