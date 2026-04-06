package com.agri_system.application.productpackage.service;

import com.agri_system.application.productpackage.dto.command.PackageItemDTO;
import com.agri_system.application.productpackage.dto.command.ProductPackageCreateCommand;
import com.agri_system.application.productpackage.dto.command.ProductPackageUpdateCommand;
import com.agri_system.application.productpackage.dto.query.ProductPackageQuery;
import com.agri_system.application.productpackage.dto.vo.ProductPackageDetailVO;
import com.agri_system.application.productpackage.dto.vo.ProductPackageItemVO;
import com.agri_system.application.productpackage.exception.PackageErrorCode;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.agri_system.domain.productpackage.service.ProductPackageDomainService;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 套餐应用服务
 * 负责业务流程编排，不包含业务规则
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class ProductPackageApplicationService {
    
    private final ProductPackageRepository packageRepository;
    private final ProductPackageItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final ProductPackageDomainService domainService;
    private final InventoryRepository inventoryRepository;
    
    // ==================== 套餐主档管理 ====================
    
    /**
     * 创建套餐
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPackage(ProductPackageCreateCommand command) {
        // 生成套餐编码
        String code = generateUniqueCode();
        
        // 校验编码唯一性
        packageRepository.findByCode(code)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(PackageErrorCode.PACKAGE_CODE_EXISTS + ": 套餐编码重复");
                });
        
        // 转换明细DTO为实体
        List<ProductPackageItem> items = convertToItems(command.getItems(), null);
        
        // 校验明细（包含重复商品校验、商品存在性校验、数量校验）
        domainService.validatePackageItems(items);
        
        // 创建套餐实体
        ProductPackage productPackage = new ProductPackage();
        productPackage.setCode(code);
        productPackage.setName(command.getName());
        productPackage.setSuggestedRetailPrice(command.getSuggestedRetailPrice());
        productPackage.setVipPrice(command.getVipPrice());
        productPackage.setMinPrice(command.getMinPrice());
        productPackage.setStatus(0); // 默认下架
        productPackage.setRemark(command.getRemark());
        productPackage.setCreatedAt(LocalDateTime.now());
        productPackage.setUpdatedAt(LocalDateTime.now());
        
        packageRepository.save(productPackage);
        
        // 保存明细
        items.forEach(item -> {
            item.setPackageId(productPackage.getId());
            item.setCreatedAt(LocalDateTime.now());
        });
        itemRepository.saveBatch(items);
        
        return productPackage.getId();
    }
    
    /**
     * 更新套餐（采用全量替换明细模式）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePackage(ProductPackageUpdateCommand command) {
        ProductPackage productPackage = packageRepository.findById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));
        
        if (productPackage.isDeleted()) {
            throw new IllegalStateException("已删除的套餐不能修改");
        }
        
        // 转换明细DTO为实体
        List<ProductPackageItem> newItems = convertToItems(command.getItems(), command.getId());
        
        // 校验明细
        domainService.validatePackageItems(newItems);
        
        // 更新套餐主信息
        productPackage.setName(command.getName());
        productPackage.setSuggestedRetailPrice(command.getSuggestedRetailPrice());
        productPackage.setVipPrice(command.getVipPrice());
        productPackage.setMinPrice(command.getMinPrice());
        productPackage.setRemark(command.getRemark());
        productPackage.setUpdatedAt(LocalDateTime.now());
        
        packageRepository.save(productPackage);
        
        // 全量替换明细：先删除旧明细，再插入新明细
        itemRepository.deleteByPackageId(command.getId());
        
        newItems.forEach(item -> {
            item.setPackageId(command.getId());
            item.setCreatedAt(LocalDateTime.now());
        });
        itemRepository.saveBatch(newItems);
    }
    
    /**
     * 删除套餐（软删除，不校验订单引用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePackage(Long id) {
        // 校验套餐存在
        packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));
        
        packageRepository.delete(id);
    }
    
    /**
     * 查询套餐详情
     */
    public ProductPackageDetailVO getPackageDetail(Long id) {
        ProductPackage productPackage = packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));
        
        ProductPackageDetailVO vo = convertToDetailVO(productPackage);
        
        // 查询明细列表
        List<ProductPackageItem> items = itemRepository.findItemsByPackageId(id);
        List<ProductPackageItemVO> itemVOs = convertToItemVOs(items);
        vo.setItems(itemVOs);
        
        return vo;
    }
    
    /**
     * 套餐分页查询（支持名称、编码、状态）
     * 如果提供了 storeId，会计算套餐的实时库存
     */
    public Page<ProductPackageDetailVO> queryPackages(ProductPackageQuery query) {
        Page<ProductPackage> packagePage = packageRepository.findPage(
                query.getCode(),
                query.getName(),
                query.getStatus(),
                query.getPageNum(),
                query.getPageSize()
        );
        
        // 转换为VO
        List<ProductPackageDetailVO> voList = packagePage.getRecords().stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
        
        // 批量查询明细
        voList.forEach(vo -> {
            List<ProductPackageItem> items = itemRepository.findItemsByPackageId(vo.getId());
            vo.setItems(convertToItemVOs(items));
        });
        
        // 如果提供了 storeId，计算套餐库存
        if (query.getStoreId() != null) {
            try {
                calculatePackageStocks(voList, query.getStoreId());
            } catch (Exception e) {
                // 计算失败时设置默认值，避免返回null
                voList.forEach(vo -> {
                    if (vo.getStock() == null) {
                        vo.setStock(BigDecimal.ZERO);
                    }
                });
            }
        } else {
            // 未提供storeId时，设置库存为0而不是null
            voList.forEach(vo -> vo.setStock(BigDecimal.ZERO));
        }
        
        Page<ProductPackageDetailVO> voPage = new Page<>();
        voPage.setPageNumber(packagePage.getPageNumber());
        voPage.setPageSize(packagePage.getPageSize());
        voPage.setTotalRow(packagePage.getTotalRow());
        voPage.setRecords(voList);
        
        return voPage;
    }
    
    /**
     * 上架/下架操作（status 字段控制）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        ProductPackage productPackage = packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));
        
        domainService.validateStatusChange(productPackage, status);
        
        packageRepository.updateStatus(id, status);
    }
    
    // ==================== 销售支撑能力（提供给订单模块） ====================
    
    /**
     * 获取套餐明细列表（提供给订单模块调用）
     * 订单模块在扣库存时展开套餐
     * 
     * @param packageId 套餐ID
     * @return 套餐明细列表
     */
    public List<ProductPackageItem> getPackageItems(Long packageId) {
        return itemRepository.findItemsByPackageId(packageId);
    }
    
    // ==================== 私有方法：转换 ====================
    
    /**
     * 生成唯一编码
     */
    private String generateUniqueCode() {
        // 查找当天最大的编码
        String todayPrefix = "PKG" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 查询当天所有编码，找到最大的序号
        String lastCode = null;
        
        // 简单实现：查询所有编码，过滤出当天的，找到最大序号
        // 注意：实际生产环境可能需要优化，使用数据库序列或Redis等
        for (int i = 1; i <= 9999; i++) {
            String testCode = todayPrefix + String.format("%04d", i);
            Optional<ProductPackage> existing = packageRepository.findByCode(testCode);
            if (existing.isEmpty()) {
                return testCode;
            }
            lastCode = testCode;
        }
        
        // 如果当天已用完，使用DomainService生成下一个序号
        return domainService.generatePackageCode(lastCode);
    }
    
    /**
     * 转换明细DTO为实体
     */
    private List<ProductPackageItem> convertToItems(List<PackageItemDTO> itemDTOs, Long packageId) {
        return itemDTOs.stream()
                .map(dto -> {
                    ProductPackageItem item = new ProductPackageItem();
                    item.setPackageId(packageId);
                    item.setProductId(dto.getProductId());
                    item.setQuantity(dto.getQuantity());
                    item.setUnit(dto.getUnit());
                    item.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
                    return item;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 转换实体为详情VO
     */
    private ProductPackageDetailVO convertToDetailVO(ProductPackage productPackage) {
        ProductPackageDetailVO vo = new ProductPackageDetailVO();
        vo.setId(productPackage.getId());
        vo.setCode(productPackage.getCode());
        vo.setName(productPackage.getName());
        vo.setSuggestedRetailPrice(productPackage.getSuggestedRetailPrice());
        vo.setVipPrice(productPackage.getVipPrice());
        vo.setMinPrice(productPackage.getMinPrice());
        vo.setStatus(productPackage.getStatus());
        vo.setRemark(productPackage.getRemark());
        vo.setCreatedAt(productPackage.getCreatedAt());
        vo.setUpdatedAt(productPackage.getUpdatedAt());
        return vo;
    }
    
    /**
     * 转换明细实体为VO
     */
    private List<ProductPackageItemVO> convertToItemVOs(List<ProductPackageItem> items) {
        // 批量查询商品信息（优化：可以后续优化为一次查询）
        Map<Long, Product> productMap = items.stream()
                .map(ProductPackageItem::getProductId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> productRepository.findById(id).orElse(null)
                ));
        
        return items.stream()
                .map(item -> {
                    ProductPackageItemVO vo = new ProductPackageItemVO();
                    vo.setId(item.getId());
                    vo.setProductId(item.getProductId());
                    Product product = productMap.get(item.getProductId());
                    if (product != null) {
                        vo.setProductName(product.getName());
                    }
                    vo.setQuantity(item.getQuantity());
                    vo.setUnit(item.getUnit());
                    vo.setSortOrder(item.getSortOrder());
                    return vo;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 计算套餐库存（木桶算法 + 主辅单位归一化）
     * 
     * 算法说明：
     * 1. 获取所有子商品的库存（主单位）和商品信息（主辅单位、换算比例）
     * 2. 对每个套餐，遍历其子商品：
     *    a. 将套餐需求量归一化为主单位（处理主辅单位换算）
     *    b. 计算该商品能组成多少套：floor(库存 / 归一化需求量)
     *    c. 取所有子商品的最小值（木桶原理）
     * 3. 将计算结果设置为套餐库存
     * 
     * 单位换算规则：
     * - 情况A：套餐需求单位 = 商品主单位 → 无需转换
     * - 情况B：套餐需求单位 = 商品辅单位 → 需求量 / 换算比例
     *   例如：商品主单位=箱，辅单位=瓶，比例=20（1箱=20瓶）
     *        套餐需要5瓶 → 归一化为 5/20 = 0.25箱
     *        库存10箱 → 可组成 10/0.25 = 40套
     * - 情况C：单位不匹配 → 兜底使用原数量（假设为主单位）
     * 
     * @param voList 套餐VO列表
     * @param storeId 门店ID
     */
    private void calculatePackageStocks(List<ProductPackageDetailVO> voList, Long storeId) {
        if (voList == null || voList.isEmpty() || storeId == null) {
            return;
        }
        
        // 1. 收集所有套餐涉及的商品ID
        List<Long> allProductIds = voList.stream()
                .flatMap(vo -> vo.getItems() != null ? vo.getItems().stream() : java.util.stream.Stream.empty())
                .map(com.agri_system.application.productpackage.dto.vo.ProductPackageItemVO::getProductId)
                .distinct()
                .collect(Collectors.toList());
        
        if (allProductIds.isEmpty()) {
            // 如果没有商品，所有套餐库存为0
            voList.forEach(vo -> vo.setStock(BigDecimal.ZERO));
            return;
        }
        
        // 2. 批量查询所有商品的库存
        Map<Long, BigDecimal> inventoryMap = inventoryRepository.findInventoryByStoreAndProducts(storeId, allProductIds);
        
        // 3. 批量查询所有商品信息（用于单位换算）
        Map<Long, Product> productMap = allProductIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> productRepository.findById(id).orElse(null)
                ));
        
        // 4. 对每个套餐计算库存（木桶算法）
        for (ProductPackageDetailVO vo : voList) {
            if (vo.getItems() == null || vo.getItems().isEmpty()) {
                vo.setStock(BigDecimal.ZERO);
                continue;
            }
            
            BigDecimal minPackageCount = null;
            
            for (com.agri_system.application.productpackage.dto.vo.ProductPackageItemVO item : vo.getItems()) {
                Long productId = item.getProductId();
                Product product = productMap.get(productId);
                
                if (product == null) {
                    // 商品不存在，该套餐无法组成
                    minPackageCount = BigDecimal.ZERO;
                    break;
                }
                
                // 获取商品库存（主单位）
                BigDecimal productStock = inventoryMap.getOrDefault(productId, BigDecimal.ZERO);
                
                // 获取套餐明细中需要的数量
                BigDecimal requiredQuantity = item.getQuantity();
                if (requiredQuantity == null || requiredQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    // 需求数量无效，该套餐无法组成
                    minPackageCount = BigDecimal.ZERO;
                    break;
                }
                
                // 处理单位换算 - 将套餐需求量归一化为主单位
                String itemUnit = item.getUnit();
                BigDecimal normalizedRequiredQuantity = requiredQuantity;
                
                // 情况A：套餐明细单位 = 商品主单位，无需转换
                if (itemUnit != null && product.getMainUnit() != null && itemUnit.equals(product.getMainUnit())) {
                    normalizedRequiredQuantity = requiredQuantity;
                }
                // 情况B：套餐明细单位 = 商品辅单位，需要转换为主单位
                else if (itemUnit != null && product.getAuxUnit() != null && itemUnit.equals(product.getAuxUnit())) {
                    if (product.getMainToAuxRatio() != null && product.getMainToAuxRatio().compareTo(BigDecimal.ZERO) > 0) {
                        // 辅单位转主单位：数量 / 比例
                        // 例如：5瓶 / 20(瓶/箱) = 0.25箱
                        normalizedRequiredQuantity = requiredQuantity.divide(product.getMainToAuxRatio(), 6, RoundingMode.HALF_UP);
                    }
                }
                // 情况C：单位不匹配或无法识别，兜底使用原数量（假设为主单位）
                else {
                    normalizedRequiredQuantity = requiredQuantity;
                }
                
                // 计算该商品能组成多少套套餐
                // 单品最大套数 = floor(单品库存 / 归一化需求量)
                BigDecimal packageCount = BigDecimal.ZERO;
                if (normalizedRequiredQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    packageCount = productStock.divide(normalizedRequiredQuantity, 0, RoundingMode.FLOOR);
                }
                
                // 取最小值（木桶原理）
                if (minPackageCount == null || packageCount.compareTo(minPackageCount) < 0) {
                    minPackageCount = packageCount;
                }
            }
            
            // 设置套餐库存
            BigDecimal finalStock = minPackageCount != null ? minPackageCount : BigDecimal.ZERO;
            vo.setStock(finalStock);
        }
    }
}

