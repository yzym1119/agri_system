package com.agri_system.application.productpackage.service;

import com.agri_system.application.productpackage.dto.command.PackageItemDTO;
import com.agri_system.application.productpackage.dto.command.ProductPackageCreateCommand;
import com.agri_system.application.productpackage.dto.command.ProductPackageUpdateCommand;
import com.agri_system.application.productpackage.dto.query.ProductPackageQuery;
import com.agri_system.application.productpackage.dto.vo.ProductPackageDetailVO;
import com.agri_system.application.productpackage.dto.vo.ProductPackageItemVO;
import com.agri_system.application.productpackage.exception.PackageErrorCode;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.inventory.repository.InventoryBatchBalanceRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.domain.performance.repository.CommissionRuleRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductUnit;
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
import java.util.LinkedHashMap;
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
    private final InventoryBatchBalanceRepository balanceRepository;
    private final ProductUnitService productUnitService;
    private final OrderItemRepository orderItemRepository;
    private final CommissionRuleRepository commissionRuleRepository;
    
    // ==================== 套餐主档管理 ====================
    
    /**
     * 创建套餐
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPackage(ProductPackageCreateCommand command) {
        PackagePricingService.validatePrices(command.getSuggestedRetailPrice(), command.getVipPrice(), command.getMinPrice());
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
        validateMaterials(items);
        
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
        ProductPackage productPackage = packageRepository.findByIdForUpdate(command.getId())
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));
        
        if (productPackage.isDeleted()) {
            throw new IllegalStateException("已删除的套餐不能修改");
        }
        PackagePricingService.validatePrices(command.getSuggestedRetailPrice(), command.getVipPrice(), command.getMinPrice());
        if (productPackage.isOnline() && command.getSuggestedRetailPrice().signum() <= 0) {
            throw new IllegalArgumentException("上架套餐必须有大于0的零售价（元/套）");
        }
        
        // 转换明细DTO为实体
        List<ProductPackageItem> newItems = convertToItems(command.getItems(), command.getId());
        
        // 校验明细
        validateMaterials(newItems);
        
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
     * 删除套餐（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePackage(Long id) {
        ProductPackage productPackage = packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));

        if (productPackage.isDeleted()) {
            throw new IllegalStateException("套餐已删除");
        }
        if (productPackage.isOnline()) {
            throw new IllegalStateException("上架中的套餐不能直接删除，请先下架后再删除");
        }

        long orderRefCount = orderItemRepository.countByPackageId(id);
        if (orderRefCount > 0) {
            throw new IllegalStateException("该套餐已被历史订单引用，无法删除");
        }

        long commissionRuleCount = commissionRuleRepository.countEnabledByCategoryAndTargetId(
                CommissionRule.Category.PACKAGE.getCode(), id);
        if (commissionRuleCount > 0) {
            throw new IllegalStateException("该套餐仍被启用中的套餐提成规则引用，无法删除");
        }

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
            calculatePackageStocks(voList, query.getStoreId());
        } else {
            // 未指定门店时未知，不能假装本店无库存。
            voList.forEach(vo -> vo.setStock(null));
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
        ProductPackage productPackage = packageRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(PackageErrorCode.PACKAGE_NOT_FOUND + ": 套餐不存在"));
        
        domainService.validateStatusChange(productPackage, status);
        if (Integer.valueOf(1).equals(status)) {
            PackagePricingService.validatePrices(productPackage.getSuggestedRetailPrice(), productPackage.getVipPrice(), productPackage.getMinPrice());
            if (productPackage.getSuggestedRetailPrice().signum() <= 0) {
                throw new IllegalArgumentException("上架套餐必须有大于0的零售价（元/套）");
            }
            validateMaterials(itemRepository.findItemsByPackageId(id));
        }
        
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
        if (itemDTOs == null || itemDTOs.isEmpty()) {
            throw new IllegalArgumentException(PackageErrorCode.PACKAGE_MUST_HAVE_ITEMS + ": 套餐必须至少包含一个商品");
        }
        return itemDTOs.stream()
                .map(dto -> {
                    if (dto == null) throw new IllegalArgumentException("套餐物料不能为空");
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

    /** 档案、配置替换与上架共用当前商品和持久化销售单位校验。 */
    private void validateMaterials(List<ProductPackageItem> items) {
        domainService.validatePackageItems(items);
        for (ProductPackageItem item : items) {
            BigDecimal quantity = item.getQuantity();
            if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4
                    || quantity.compareTo(new BigDecimal("999999.9999")) > 0) {
                throw new IllegalArgumentException("套餐物料数量必须大于0、最多四位小数且在存储范围内");
            }
            ProductUnit unit = productUnitService.requireEnabledUnit(item.getProductId(), item.getUnit(),
                    ProductUnitService.UnitOperation.SALE);
            item.setUnit(unit.getUnitName());
        }
    }
    
    /** 可售套数：门店可用基准库存 / 每套基准需求，同商品需求先汇总。 */
    private void calculatePackageStocks(List<ProductPackageDetailVO> voList, Long storeId) {
        if (voList == null || voList.isEmpty() || storeId == null) return;
        var productIds = voList.stream().filter(v -> v.getItems() != null)
                .flatMap(v -> v.getItems().stream()).map(ProductPackageItemVO::getProductId).distinct().toList();
        var stockRows = productIds.isEmpty() ? List.<com.agri_system.domain.inventory.vo.SaleableStock>of()
                : balanceRepository.findSaleableStock(storeId, productIds);
        var stockByProduct = stockRows.stream().collect(Collectors.groupingBy(com.agri_system.domain.inventory.vo.SaleableStock::productId));
        Map<Long, BigDecimal> inventory = new LinkedHashMap<>();
        Map<Long, String> baseNames = new LinkedHashMap<>();
        for (Long productId : productIds) {
            try {
                var baseUnits = productUnitService.getUnits(productId).stream()
                        .filter(u -> u.getId() != null && Integer.valueOf(1).equals(u.getStatus())
                                && Integer.valueOf(1).equals(u.getIsBaseUnit()) && u.getBaseQuantityPerUnit() != null && BigDecimal.ONE.compareTo(u.getBaseQuantityPerUnit()) == 0
                                && u.getUnitName() != null && !u.getUnitName().isBlank()).toList();
                if (baseUnits.size() != 1) continue;
                baseNames.put(productId, baseUnits.get(0).getUnitName());
                var rows = stockByProduct.getOrDefault(productId, List.of());
                if (rows.stream().anyMatch(r -> !baseUnits.get(0).getId().equals(r.baseUnitId())
                        || r.availableBaseQuantity() == null || r.availableBaseQuantity().signum() < 0)) continue;
                inventory.put(productId, rows.stream().map(com.agri_system.domain.inventory.vo.SaleableStock::availableBaseQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            } catch (IllegalArgumentException | IllegalStateException incompleteUnits) {
                // 缺少真实单位证据时保持未知，不猜当前单位或零库存。
            }
        }
        for (ProductPackageDetailVO vo : voList) {
            vo.setStock(null);
            if (vo.getItems() == null || vo.getItems().isEmpty()) continue;
            try {
                Map<Long, BigDecimal> demand = new LinkedHashMap<>();
                for (ProductPackageItemVO item : vo.getItems()) {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new IllegalArgumentException("套餐物料不存在"));
                    if (!Integer.valueOf(1).equals(product.getStatus())) throw new IllegalArgumentException("套餐物料已停用");
                    ProductUnit unit = productUnitService.requireEnabledUnit(item.getProductId(), item.getUnit(),
                            ProductUnitService.UnitOperation.SALE);
                    BigDecimal quantity = item.getQuantity();
                    BigDecimal coefficient = unit.getBaseQuantityPerUnit();
                    if (quantity == null || quantity.signum() <= 0 || coefficient == null || coefficient.signum() <= 0) {
                        throw new IllegalArgumentException("套餐物料数量或基准单位系数无效");
                    }
                    BigDecimal baseDemand = com.agri_system.domain.product.service.QuantityGranularityPolicy
                            .requireBaseQuantity(baseNames.get(item.getProductId()), quantity.multiply(coefficient));
                    demand.merge(item.getProductId(), baseDemand, BigDecimal::add);
                }
                BigDecimal minimum = null;
                for (Map.Entry<Long, BigDecimal> entry : demand.entrySet()) {
                    BigDecimal available = inventory.get(entry.getKey());
                    if (available == null) throw new IllegalArgumentException("套餐物料库存单位待核对");
                    BigDecimal count = available.divide(entry.getValue(), 0, RoundingMode.FLOOR);
                    minimum = minimum == null ? count : minimum.min(count);
                }
                vo.setStock(minimum == null ? BigDecimal.ZERO : minimum);
            } catch (IllegalArgumentException | IllegalStateException invalidConfiguration) {
                // 单个套餐单位失效保持未知，不能影响其它套餐列表。
                vo.setStock(null);
            }
        }
    }
}
