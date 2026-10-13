package com.agri_system.application.product.service;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.product.dto.command.*;
import com.agri_system.application.product.dto.query.ProductQuery;
import com.agri_system.application.product.dto.vo.CategoryTreeVO;
import com.agri_system.application.product.dto.vo.PriceContext;
import com.agri_system.application.product.dto.vo.PriceQuote;
import com.agri_system.application.product.dto.vo.PriceRuleVO;
import com.agri_system.application.product.dto.vo.ProductDetailVO;
import com.agri_system.application.product.dto.vo.ProductUnitVO;
import com.agri_system.application.product.dto.vo.UnitConversionVO;
import com.agri_system.application.product.exception.ProductErrorCode;
import com.agri_system.application.storage.dto.FileUploadCommand;
import com.agri_system.domain.common.service.CodeGenerationService;
import com.agri_system.domain.inventory.repository.InventoryRepository;
import com.agri_system.domain.order.repository.OrderItemRepository;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.model.ProductCategory;
import com.agri_system.domain.product.model.ProductPrice;
import com.agri_system.domain.product.model.ProductUnitConversion;
import com.agri_system.domain.product.repository.ProductCategoryRepository;
import com.agri_system.domain.product.repository.ProductPriceRepository;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.repository.ProductUnitConversionRepository;
import com.agri_system.domain.product.service.ProductDomainService;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import com.agri_system.domain.purchase.repository.PurchaseItemRepository;
import com.agri_system.domain.storage.FileStorageRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 商品应用服务
 * 负责业务流程编排，不包含业务规则
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class ProductApplicationService {
    
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductUnitConversionRepository unitConversionRepository;
    private final ProductPriceRepository priceRepository;
    private final ProductDomainService domainService;
    private final FileStorageRepository fileStorageRepository;
    private final com.agri_system.application.common.service.CodeGenerationApplicationService codeGenerationService;
    private final OrderItemRepository orderItemRepository;
    private final ProductPackageItemRepository productPackageItemRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductUnitService productUnitService;
    private final ProductPricingService productPricingService;
    
    // ==================== 商品基本档案 ====================
    
    /**
     * 创建商品
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ProductCreateCommand command) {
        validatePrices(command.getSuggestedRetailPrice(), command.getVipPrice(), command.getMinPrice(), command.getOnlinePrice());
        // 自动生成编码（如果未提供）
        String productCode = command.getCode();
        if (productCode == null || productCode.isBlank()) {
            productCode = codeGenerationService.generateNextCode(CodeGenerationService.CodeType.PRODUCT);
        }
        
        // 校验编码唯一性
        productRepository.findByCode(productCode)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(ProductErrorCode.PRODUCT_CODE_EXISTS + ": 商品编码已存在");
                });
        
        // 校验分类
        if (command.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(command.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.CATEGORY_NOT_FOUND + ": 分类不存在"));
            domainService.validateCategory(category);
        }
        
        // 校验名称+规格+厂家唯一
        productRepository.findByNameSpecManufacturer(
                        command.getName(),
                        command.getSpecification(),
                        command.getManufacturer())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("商品已存在：同名同规格同厂家");
                });
        
        // 创建商品实体
        Product product = new Product();
        product.setCode(productCode);
        product.setName(command.getName());
        product.setCategoryId(command.getCategoryId());
        product.setSpecification(command.getSpecification());
        product.setDosageForm(command.getDosageForm());
        product.setManufacturer(command.getManufacturer());
        product.setMainUnit(command.getMainUnit());
        product.setAuxUnit(command.getAuxUnit());
        product.setMainToAuxRatio(command.getMainToAuxRatio());
        product.setSuggestedRetailPrice(command.getSuggestedRetailPrice());
        product.setVipPrice(command.getVipPrice());
        product.setMinPrice(command.getMinPrice());
        // avg_cost_price 不设置，由采购模块维护
        product.setImageUrl(command.getImageUrl());
        product.setDetailText(command.getDetailText());
        product.setIsOnline(command.getIsOnline() != null ? command.getIsOnline() : 0);
        // online_price 仅保留历史存储，当前线上与门店使用同一报价。
        product.setBaseShippingFee(command.getBaseShippingFee() != null ? command.getBaseShippingFee() : java.math.BigDecimal.ZERO);
        product.setAllowSpecialFund(command.getAllowSpecialFund() != null ? command.getAllowSpecialFund() : 0);
        product.setStatus(1);
        product.setRemark(command.getRemark());
        product.setBarcode(command.getBarcode());
        product.setRegistrationNo(command.getRegistrationNo());
        product.setStorageConditions(command.getStorageConditions());
        product.setShelfLifeDays(command.getShelfLifeDays());
        product.setUsageMethod(command.getUsageMethod());
        product.setMinStock(command.getMinStock());
        product.setMaxStock(command.getMaxStock());
        product.setReorderQuantity(command.getReorderQuantity());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        
        // 设置创建人和更新人ID
        Long currentStaffId = getCurrentStaffId();
        product.setCreatedBy(currentStaffId);
        product.setUpdatedBy(currentStaffId);
        
        productRepository.save(product);
        productUnitService.initializeUnits(product);
        return product.getId();
    }
    
    /**
     * 更新商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(ProductUpdateCommand command) {
        Product product = productRepository.findById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        // 校验分类
        if (command.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(command.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.CATEGORY_NOT_FOUND + ": 分类不存在"));
            domainService.validateCategory(category);
        }
        
        // 校验名称+规格+厂家唯一（排除自身）—— 仅当名称/规格/厂家有传值时才校验
        String checkName = command.getName() != null ? command.getName() : product.getName();
        String checkSpec = command.getSpecification() != null ? command.getSpecification() : product.getSpecification();
        String checkMfr = command.getManufacturer() != null ? command.getManufacturer() : product.getManufacturer();
        productRepository.findByNameSpecManufacturerExcludeId(
                        checkName,
                        checkSpec,
                        checkMfr,
                        command.getId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("商品已存在：同名同规格同厂家");
                });

        validatePrices(command.getSuggestedRetailPrice() != null ? command.getSuggestedRetailPrice() : product.getSuggestedRetailPrice(),
                command.getVipPrice() != null ? command.getVipPrice() : product.getVipPrice(),
                command.getMinPrice() != null ? command.getMinPrice() : product.getMinPrice(), command.getOnlinePrice());

        // 更新商品信息（只更新非空字段，避免清空未传递的字段）
        if (command.getName() != null) {
            product.setName(command.getName());
        }
        if (command.getCategoryId() != null) {
            product.setCategoryId(command.getCategoryId());
        }
        if (command.getSpecification() != null) {
            product.setSpecification(command.getSpecification());
        }
        if (command.getDosageForm() != null) {
            product.setDosageForm(command.getDosageForm());
        }
        if (command.getManufacturer() != null) {
            product.setManufacturer(command.getManufacturer());
        }
        if (command.getMainUnit() != null && !command.getMainUnit().trim().equals(product.getMainUnit())) {
            throw new IllegalArgumentException("商品基准单位不可通过档案变更，请使用单位管理");
        }
        if (command.getAuxUnit() != null
                && !java.util.Objects.equals(command.getAuxUnit().trim().isEmpty() ? null : command.getAuxUnit().trim(),
                    product.getAuxUnit())) {
            throw new IllegalArgumentException("商品辅单位不可通过档案变更，请使用单位管理");
        }
        if (command.getMainToAuxRatio() != null
                && (product.getMainToAuxRatio() == null
                    || command.getMainToAuxRatio().compareTo(product.getMainToAuxRatio()) != 0)) {
            throw new IllegalArgumentException("商品单位换算比例不可通过档案变更，请使用单位管理");
        }

        // 辅单位：仅在明确传值时更新
        if (command.getAuxUnit() != null) {
            String newAuxUnit = command.getAuxUnit().trim().isEmpty() ? null : command.getAuxUnit();
            product.setAuxUnit(newAuxUnit);
            if (newAuxUnit == null) {
                product.setMainToAuxRatio(null);
            } else if (command.getMainToAuxRatio() != null) {
                product.setMainToAuxRatio(command.getMainToAuxRatio());
            }
        }
        if (command.getSuggestedRetailPrice() != null) {
            product.setSuggestedRetailPrice(command.getSuggestedRetailPrice());
        }
        if (command.getVipPrice() != null) {
            product.setVipPrice(command.getVipPrice());
        }
        if (command.getMinPrice() != null) {
            product.setMinPrice(command.getMinPrice());
        }
        if (command.getImageUrl() != null) {
            product.setImageUrl(command.getImageUrl());
        }
        if (command.getDetailText() != null) {
            product.setDetailText(command.getDetailText());
        }

        if (command.getBaseShippingFee() != null) {
            product.setBaseShippingFee(command.getBaseShippingFee());
        }
        if (command.getIsOnline() != null) {
            product.setIsOnline(command.getIsOnline());
        }
        // 允许专用金字段：0 和 1 都是有效值，需要明确处理
        // 如果传递了值（包括0），则更新；如果为null，则不更新（保持原值）
        if (command.getAllowSpecialFund() != null) {
            product.setAllowSpecialFund(command.getAllowSpecialFund());
            // 无论值是 0 还是 1，都使用强制更新确保数据库被正确更新
            // MyBatis-Flex 的默认 update 方法可能不会正确处理 Integer 类型的 0 值
        }
        
        if (command.getRemark() != null) {
            product.setRemark(command.getRemark());
        }
        if (command.getBarcode() != null) {
            product.setBarcode(command.getBarcode());
        }
        if (command.getRegistrationNo() != null) {
            product.setRegistrationNo(command.getRegistrationNo());
        }
        if (command.getStorageConditions() != null) {
            product.setStorageConditions(command.getStorageConditions());
        }
        if (command.getShelfLifeDays() != null) {
            product.setShelfLifeDays(command.getShelfLifeDays());
        }
        if (command.getUsageMethod() != null) {
            product.setUsageMethod(command.getUsageMethod());
        }
        if (command.getMinStock() != null) {
            product.setMinStock(command.getMinStock());
        }
        if (command.getMaxStock() != null) {
            product.setMaxStock(command.getMaxStock());
        }
        if (command.getReorderQuantity() != null) {
            product.setReorderQuantity(command.getReorderQuantity());
        }
        product.setUpdatedAt(LocalDateTime.now());
        
        // 设置更新人ID
        Long currentStaffId = getCurrentStaffId();
        product.setUpdatedBy(currentStaffId);
        
        // 使用强制更新，确保 0/null 值被正确持久化
        if (productRepository instanceof com.agri_system.infrastructure.product.repository.ProductRepositoryImpl) {
            ((com.agri_system.infrastructure.product.repository.ProductRepositoryImpl) productRepository).updateForce(product);
        } else {
            productRepository.save(product);
        }
    }
    
    /**
     * 查询商品详情
     */
    public ProductDetailVO getProductDetail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        ProductDetailVO vo = convertToDetailVO(product);
        
        // 填充分类名称
        if (product.getCategoryId() != null) {
            categoryRepository.findById(product.getCategoryId())
                    .ifPresent(category -> vo.setCategoryName(category.getName()));
        }
        enrichUnitFields(vo, product.getId());
        
        return vo;
    }
    
    /**
     * 分页查询商品
     */
    public Page<ProductDetailVO> queryProducts(ProductQuery query) {
        Page<Product> productPage = productRepository.findPage(
                query.getCode(),
                query.getName(),
                query.getCategoryId(),
                query.getIsOnline(),
                query.getCreatedStartTime(),
                query.getCreatedEndTime(),
                query.getPageNum(),
                query.getPageSize()
        );
        
        // 转换为VO
        List<ProductDetailVO> voList = productPage.getRecords().stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList());
        
        voList.forEach(vo -> {
            vo.setStrategyCount(0);
            enrichUnitFields(vo, vo.getId());
        });

        // 填充分类名称
        voList.forEach(vo -> {
            if (vo.getCategoryId() != null) {
                categoryRepository.findById(vo.getCategoryId())
                        .ifPresent(category -> vo.setCategoryName(category.getName()));
            }
        });
        
        Page<ProductDetailVO> voPage = new Page<>();
        voPage.setPageNumber(productPage.getPageNumber());
        voPage.setPageSize(productPage.getPageSize());
        voPage.setTotalRow(productPage.getTotalRow());
        voPage.setRecords(voList);
        
        return voPage;
    }
    
    /**
     * 上架/下架商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOnlineStatus(Long id, Integer isOnline) {
        productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        productRepository.updateOnlineStatus(id, isOnline);
    }
    
    /**
     * 删除商品
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));

        long orderRefCount = orderItemRepository.countByProductId(id);
        if (orderRefCount > 0) {
            throw new IllegalStateException("该商品已被订单明细引用，无法删除");
        }

        long packageRefCount = productPackageItemRepository.countByProductId(id);
        if (packageRefCount > 0) {
            throw new IllegalStateException("该商品已被套餐明细引用，无法删除");
        }

        long purchaseRefCount = purchaseItemRepository.countByProductId(id);
        if (purchaseRefCount > 0) {
            throw new IllegalStateException("该商品已被采购明细引用，无法删除");
        }

        java.math.BigDecimal currentStock = inventoryRepository.sumQuantityByProduct(id, null);
        if (currentStock != null && currentStock.compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("该商品当前仍有库存，无法删除");
        }

        productRepository.delete(id);
    }

    /**
     * 上传商品主图至 OSS.
     */
    public String uploadProductImage(FileUploadCommand command) {
        if (command.getSize() <= 0) {
            throw new IllegalArgumentException(ProductErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED + ": 文件为空");
        }
        String extension = extractExtension(command.getOriginalFilename());
        String objectKey = buildObjectKey(command.getModule(), extension);
        try (InputStream inputStream = command.openStream()) {
            return fileStorageRepository.upload(objectKey, inputStream, command.getSize(), command.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException(ProductErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED + ": 读取文件失败", e);
        }
    }

    private String buildObjectKey(String module, String extension) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String cleanModule = module == null || module.isBlank() ? "common" : module.toLowerCase(Locale.ROOT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String suffix = extension.isEmpty() ? "" : "." + extension;
        return String.format("%s/%s/%s%s", cleanModule, today, uuid, suffix);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        return ext.toLowerCase(Locale.ROOT);
    }
    
    // ==================== 商品分类管理 ====================
    
    /**
     * 创建分类
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(CategoryCreateCommand command) {
        // 如果编码为空，自动生成
        final String code = (command.getCode() == null || command.getCode().trim().isEmpty())
                ? codeGenerationService.generateNextCode(com.agri_system.domain.common.service.CodeGenerationService.CodeType.CATEGORY)
                : command.getCode();
        
        // 校验编码唯一性
        categoryRepository.findByCode(code)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("分类编码已存在: " + code);
                });
        
        // 确定层级
        Integer level = 1;
        if (command.getParentId() != null && command.getParentId() > 0) {
            ProductCategory parent = categoryRepository.findById(command.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.CATEGORY_NOT_FOUND + ": 父分类不存在"));
            level = parent.getLevel() + 1;
        }
        
        ProductCategory category = new ProductCategory();
        category.setCode(code);
        category.setName(command.getName());
        category.setParentId(command.getParentId());
        category.setLevel(level);
        category.setSortOrder(command.getSortOrder() != null ? command.getSortOrder() : 0);
        category.setStatus(1);
        category.setRemark(command.getRemark());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        
        categoryRepository.save(category);
        return category.getId();
    }
    
    /**
     * 更新分类
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryUpdateCommand command) {
        ProductCategory category = categoryRepository.findById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.CATEGORY_NOT_FOUND + ": 分类不存在"));
        
        // 关键修复：无论 command.getParentId() 是什么值，都要处理父分类更新
        // 因为前端在清空父分类时会明确传递 null，我们需要识别并处理
        Long newParentId = command.getParentId();
        
        if (newParentId != null && newParentId > 0) {
            // 设置了有效的父分类ID
            // 不能将自己设为父分类
            if (newParentId.equals(command.getId())) {
                throw new IllegalArgumentException("不能将自己设为父分类");
            }
            
            // 检查是否会造成循环：确保父分类不是当前分类的子分类
            if (isCircularReference(command.getId(), newParentId)) {
                throw new IllegalArgumentException("不能将父分类设置为当前分类的子分类，这会造成循环引用");
            }
            
            // 更新父分类和层级
            ProductCategory parent = categoryRepository.findById(newParentId)
                    .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.CATEGORY_NOT_FOUND + ": 父分类不存在"));
            category.setParentId(newParentId);
            category.setLevel(parent.getLevel() + 1);
        } else {
            // parentId 为 null 或 0，设置为顶级分类（清空父分类）
            category.setParentId(null);
            category.setLevel(1);
        }
        
        category.setName(command.getName());
        category.setSortOrder(command.getSortOrder());
        if (command.getStatus() != null) {
            category.setStatus(command.getStatus());
        }
        category.setRemark(command.getRemark());
        category.setUpdatedAt(LocalDateTime.now());
        
        categoryRepository.save(category);
    }
    
    /**
     * 检查是否会造成循环引用
     * @param categoryId 当前分类ID
     * @param newParentId 新的父分类ID
     * @return true表示会造成循环引用
     */
    private boolean isCircularReference(Long categoryId, Long newParentId) {
        // 递归检查：如果newParentId是categoryId的子分类，则会造成循环
        List<ProductCategory> children = categoryRepository.findByParentId(categoryId);
        for (ProductCategory child : children) {
            if (child.getId().equals(newParentId)) {
                return true; // 直接子分类
            }
            // 递归检查子分类的子分类
            if (isCircularReference(child.getId(), newParentId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 删除分类
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        // 检查分类是否存在
        categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.CATEGORY_NOT_FOUND + ": 分类不存在"));
        
        // 校验是否有子分类
        List<ProductCategory> children = categoryRepository.findByParentId(id);
        if (!children.isEmpty()) {
            throw new IllegalStateException("该分类下存在子分类，无法删除。请先删除或移动子分类");
        }
        
        // 校验是否被商品引用
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new IllegalStateException(ProductErrorCode.CATEGORY_HAS_PRODUCTS + ": 分类下存在商品，无法删除");
        }
        
        categoryRepository.delete(id);
    }
    
    /**
     * 查询分类树
     */
    public List<CategoryTreeVO> getCategoryTree() {
        // 查询所有分类（扁平列表），然后在前端构建树形结构
        List<ProductCategory> allCategories = categoryRepository.findAll();
        return buildCategoryTree(allCategories, null);
    }
    
    // ==================== 多单位管理 ====================
    
    /**
     * 创建单位换算
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createUnitConversion(UnitConversionCreateCommand command) {
        // 校验商品存在
        productRepository.findById(command.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(ProductErrorCode.PRODUCT_NOT_FOUND + ": 商品不存在"));
        
        // 校验单位换算配置冲突
        unitConversionRepository.findByProductIdAndUnits(
                command.getProductId(),
                command.getFromUnit(),
                command.getToUnit()
        ).ifPresent(existing -> {
            throw new IllegalArgumentException(ProductErrorCode.UNIT_CONVERSION_CONFLICT + ": 单位换算配置冲突");
        });
        
        ProductUnitConversion conversion = new ProductUnitConversion();
        conversion.setProductId(command.getProductId());
        conversion.setFromUnit(command.getFromUnit());
        conversion.setToUnit(command.getToUnit());
        conversion.setConversionRatio(command.getConversionRatio());
        conversion.setCreatedAt(LocalDateTime.now());
        conversion.setUpdatedAt(LocalDateTime.now());
        
        unitConversionRepository.save(conversion);
        return conversion.getId();
    }
    
    /**
     * 删除单位换算
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnitConversion(Long id) {
        unitConversionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("单位换算不存在"));
        unitConversionRepository.delete(id);
    }
    
    /**
     * 查询商品的所有单位换算
     */
    public List<UnitConversionVO> getUnitConversions(Long productId) {
        List<ProductUnitConversion> conversions = unitConversionRepository.findByProductId(productId);
        return conversions.stream()
                .map(this::convertToUnitConversionVO)
                .collect(Collectors.toList());
    }

    public List<ProductUnitVO> getProductUnits(Long productId) {
        return productUnitService.getUnitVOs(productId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceProductUnits(Long productId, List<ProductUnitUpsertCommand> units) {
        productUnitService.replaceUnits(productId, units);
    }
    
    // ==================== 多维价格体系 ====================
    
    /**
     * 创建价格规则
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPriceRule(PriceRuleCreateCommand command) {
        throw new IllegalArgumentException("多维价格策略已停用，请在商品档案维护零售价、会员价和最低价");
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePriceRule(PriceRuleUpdateCommand command) {
        throw new IllegalArgumentException("多维价格策略已停用，请在商品档案维护价格");
    }

    /**
     * 删除价格规则
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePriceRule(Long id) {
        throw new IllegalArgumentException("历史价格策略仅供查询，不再维护；请在商品档案维护价格");
    }

    /**
     * 查询商品的价格规则列表
     */
    public List<PriceRuleVO> getPriceRules(Long productId) {
        List<ProductPrice> prices = priceRepository.findByProductId(productId);
        return prices.stream()
                .map(this::convertToPriceRuleVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 服务端报价：按商品/门店/渠道/客户分层与销售单位解析单价
     */
    public PriceQuote quoteProductPrice(Long productId, Long storeId, String channel, Long customerId,
                                        String saleUnit, LocalDate businessDate) {
        ProductPricingService.CustomerPriceContext customer = productPricingService.resolveCustomerContext(customerId);
        return productPricingService.quote(new PriceContext(productId, storeId, channel,
                customer.customerType(), null, saleUnit, businessDate));
    }

    // ==================== 私有方法：转换 ====================
    
    /** 价格都按持久化基准单位维护；0 表示可选会员价/最低价未启用。 */
    private void validatePrices(java.math.BigDecimal retail, java.math.BigDecimal member,
                                java.math.BigDecimal floor, java.math.BigDecimal online) {
        for (java.math.BigDecimal price : new java.math.BigDecimal[]{retail, member, floor}) {
            if (price != null && (price.signum() < 0 || price.scale() > 2 || price.precision() - price.scale() > 8)) {
                throw new IllegalArgumentException("商品价格不能为负，最多两位小数且小于一亿元");
            }
        }
        if (online != null && online.signum() != 0 && (retail == null || online.compareTo(retail) != 0)) {
            throw new IllegalArgumentException("线上与门店使用统一价格，请修改商品零售价，不再单独设置线上价");
        }
        if (member != null && member.signum() > 0 && (retail == null || member.compareTo(retail) > 0)) {
            throw new IllegalArgumentException("会员价不能高于零售价");
        }
        if (floor != null && member != null && member.signum() > 0 && floor.compareTo(member) > 0) {
            throw new IllegalArgumentException("会员价不能低于最低价提醒，请核对商品价格");
        }
        if (floor != null && floor.signum() > 0 && (retail == null || floor.compareTo(retail) > 0)) {
            throw new IllegalArgumentException("最低价不能高于零售价");
        }
    }

    private ProductDetailVO convertToDetailVO(Product product) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setCode(product.getCode());
        vo.setName(product.getName());
        vo.setCategoryId(product.getCategoryId());
        vo.setSpecification(product.getSpecification());
        vo.setDosageForm(product.getDosageForm());
        vo.setManufacturer(product.getManufacturer());
        vo.setMainUnit(product.getMainUnit());
        vo.setAuxUnit(product.getAuxUnit());
        vo.setMainToAuxRatio(product.getMainToAuxRatio());
        vo.setSuggestedRetailPrice(product.getSuggestedRetailPrice());
        vo.setVipPrice(product.getVipPrice());
        vo.setMinPrice(product.getMinPrice());
        vo.setAvgCostPrice(product.getAvgCostPrice());
        vo.setImageUrl(product.getImageUrl());
        vo.setDetailText(product.getDetailText());
        vo.setIsOnline(product.getIsOnline());
        vo.setOnlinePrice(product.getSuggestedRetailPrice()); // 旧客户端展示字段使用当前零售价，禁止另一套线上价
        vo.setBaseShippingFee(product.getBaseShippingFee());
        vo.setAllowSpecialFund(product.getAllowSpecialFund());
        vo.setStatus(product.getStatus());
        vo.setRemark(product.getRemark());
        vo.setBarcode(product.getBarcode());
        vo.setRegistrationNo(product.getRegistrationNo());
        vo.setStorageConditions(product.getStorageConditions());
        vo.setShelfLifeDays(product.getShelfLifeDays());
        vo.setUsageMethod(product.getUsageMethod());
        vo.setMinStock(product.getMinStock());
        vo.setMaxStock(product.getMaxStock());
        vo.setReorderQuantity(product.getReorderQuantity());
        vo.setCreatedAt(product.getCreatedAt());
        vo.setUpdatedAt(product.getUpdatedAt());
        return vo;
    }

    private void enrichUnitFields(ProductDetailVO vo, Long productId) {
        if (productUnitService == null || productId == null) {
            return;
        }
        vo.setUnitModelStatus(productUnitService.getUnitModelStatus(productId));
        vo.setUnits(productUnitService.getUnitVOs(productId));
        if (vo.getUnits() != null) {
            vo.setPricingUnit(vo.getUnits().stream().filter(u -> Integer.valueOf(1).equals(u.getIsBaseUnit()))
                    .map(com.agri_system.application.product.dto.vo.ProductUnitVO::getUnitName).findFirst().orElse(null));
        }
    }
    
    private CategoryTreeVO convertToCategoryTreeVO(ProductCategory category, List<ProductCategory> allCategories) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(category.getId());
        vo.setCode(category.getCode());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        vo.setLevel(category.getLevel());
        vo.setSortOrder(category.getSortOrder());
        vo.setStatus(category.getStatus());
        vo.setRemark(category.getRemark());
        vo.setCreatedAt(category.getCreatedAt());
        
        // 设置父分类名称
        if (category.getParentId() != null && category.getParentId() > 0) {
            allCategories.stream()
                    .filter(cat -> cat.getId().equals(category.getParentId()))
                    .findFirst()
                    .ifPresent(parent -> vo.setParentName(parent.getName()));
        }
        
        return vo;
    }
    
    private List<CategoryTreeVO> buildCategoryTree(List<ProductCategory> categories, Long parentId) {
        return categories.stream()
                .filter(cat -> {
                    Long catParentId = cat.getParentId();
                    return (parentId == null && (catParentId == null || catParentId == 0)) ||
                           (parentId != null && parentId.equals(catParentId));
                })
                .map(category -> {
                    CategoryTreeVO vo = convertToCategoryTreeVO(category, categories);
                    vo.setChildren(buildCategoryTree(categories, category.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }
    
    private UnitConversionVO convertToUnitConversionVO(ProductUnitConversion conversion) {
        UnitConversionVO vo = new UnitConversionVO();
        vo.setId(conversion.getId());
        vo.setProductId(conversion.getProductId());
        vo.setFromUnit(conversion.getFromUnit());
        vo.setToUnit(conversion.getToUnit());
        vo.setConversionRatio(conversion.getConversionRatio());
        vo.setCreatedAt(conversion.getCreatedAt());
        vo.setUpdatedAt(conversion.getUpdatedAt());
        return vo;
    }
    
    private PriceRuleVO convertToPriceRuleVO(ProductPrice price) {
        PriceRuleVO vo = new PriceRuleVO();
        vo.setId(price.getId());
        vo.setProductId(price.getProductId());
        vo.setStoreId(price.getStoreId());
        vo.setCustomerType(price.getCustomerType());
        vo.setVipLevel(price.getVipLevel());
        vo.setPriceType(price.getPriceType());
        vo.setPrice(price.getPrice());
        vo.setEffectiveDate(price.getEffectiveDate());
        vo.setExpiryDate(price.getExpiryDate());
        vo.setStatus(price.getStatus());
        vo.setSaleUnit(price.getSaleUnit());
        vo.setChannel(price.getChannel());
        vo.setRuleVersion(price.getRuleVersion());
        vo.setCreatedAt(price.getCreatedAt());
        vo.setUpdatedAt(price.getUpdatedAt());
        return vo;
    }
    
    /**
     * 获取当前登录员工ID
     * 从Sa-Token获取，如果未登录则返回null
     */
    private Long getCurrentStaffId() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId != null && loginId.startsWith("STAFF_")) {
                return Long.parseLong(loginId.substring("STAFF_".length()));
            }
        } catch (Exception e) {
            // 忽略异常，返回null
        }
        return null;
    }
}
