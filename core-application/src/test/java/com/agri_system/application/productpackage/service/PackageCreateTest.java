package com.agri_system.application.productpackage.service;

import com.agri_system.application.productpackage.dto.command.PackageItemDTO;
import com.agri_system.application.productpackage.dto.command.ProductPackageCreateCommand;
import com.agri_system.application.productpackage.exception.PackageErrorCode;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.repository.ProductPackageItemRepository;
import com.agri_system.domain.productpackage.repository.ProductPackageRepository;
import com.agri_system.domain.productpackage.service.ProductPackageDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 套餐创建测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PackageCreateTest {
    
    @Mock
    private ProductPackageRepository packageRepository;
    
    @Mock
    private ProductPackageItemRepository itemRepository;
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private ProductPackageDomainService domainService;

    @Mock
    private ProductUnitService productUnitService;
    
    @InjectMocks
    private ProductPackageApplicationService packageService;
    
    private ProductPackageCreateCommand createCommand;
    private Product testProduct;
    
    @BeforeEach
    void setUp() {
        createCommand = new ProductPackageCreateCommand();
        createCommand.setName("测试套餐");
        createCommand.setSuggestedRetailPrice(new BigDecimal("200.00"));
        createCommand.setVipPrice(new BigDecimal("180.00"));
        createCommand.setMinPrice(new BigDecimal("150.00"));
        
        List<PackageItemDTO> items = new ArrayList<>();
        PackageItemDTO item1 = new PackageItemDTO();
        item1.setProductId(1L);
        item1.setQuantity(new BigDecimal("2.0000"));
        item1.setUnit("袋");
        items.add(item1);
        createCommand.setItems(items);
        
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("测试商品");
        testProduct.setStatus(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productUnitService.requireEnabledUnit(anyLong(), anyString(), eq(ProductUnitService.UnitOperation.SALE)))
                .thenAnswer(inv -> {
                    ProductUnit unit = new ProductUnit(); unit.setUnitName(inv.getArgument(1));
                    unit.setBaseQuantityPerUnit(BigDecimal.ONE); return unit;
                });
    }
    
    @Test
    void testCreatePackage_WithoutVipPrice_ShouldAllow() {
        // Given: 不传vip_price
        createCommand.setVipPrice(null);
        when(packageRepository.findByCode(anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            ProductPackage pkg = invocation.getArgument(0);
            pkg.setId(1L);
            return null;
        }).when(packageRepository).save(any(ProductPackage.class));
        
        // When: 创建套餐
        Long packageId = packageService.createPackage(createCommand);
        
        // Then: 应允许创建
        assertNotNull(packageId);
        verify(packageRepository, times(1)).save(argThat(pkg -> 
            pkg.getVipPrice() == null
        ));
    }
    
    @Test
    void testCreatePackage_DuplicateProducts_ShouldThrowException() {
        // Given: items包含重复商品
        List<PackageItemDTO> items = new ArrayList<>();
        PackageItemDTO item1 = new PackageItemDTO();
        item1.setProductId(1L);
        item1.setQuantity(new BigDecimal("2.0000"));
        item1.setUnit("袋");
        items.add(item1);
        
        PackageItemDTO item2 = new PackageItemDTO();
        item2.setProductId(1L); // 重复的商品ID
        item2.setQuantity(new BigDecimal("3.0000"));
        item2.setUnit("袋");
        items.add(item2);
        
        createCommand.setItems(items);
        
        when(packageRepository.findByCode(anyString())).thenReturn(Optional.empty());
        // 领域服务负责重复商品校验，createPackage 委托并向上抛出
        doThrow(new IllegalArgumentException(PackageErrorCode.PACKAGE_DUPLICATE_PRODUCTS))
                .when(domainService).validatePackageItems(any());

        // When & Then: 应抛出package-1005异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            packageService.createPackage(createCommand);
        });
        
        assertTrue(exception.getMessage().contains(PackageErrorCode.PACKAGE_DUPLICATE_PRODUCTS));
    }
    
    @Test
    void testCreatePackage_EmptyItems_ShouldThrowException() {
        // Given: items为空
        createCommand.setItems(new ArrayList<>());
        when(packageRepository.findByCode(anyString())).thenReturn(Optional.empty());
        // 领域服务负责空明细校验，createPackage 委托并向上抛出
        doThrow(new IllegalArgumentException(PackageErrorCode.PACKAGE_MUST_HAVE_ITEMS))
                .when(domainService).validatePackageItems(any());

        // When & Then: 应抛出package-1004异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            packageService.createPackage(createCommand);
        });
        
        assertTrue(exception.getMessage().contains(PackageErrorCode.PACKAGE_MUST_HAVE_ITEMS));
    }
    @Test
    void memberPriceBelowPackageFloorIsRejectedBeforeSaving() {
        createCommand.setVipPrice(new BigDecimal("140.00"));
        assertThrows(IllegalArgumentException.class, () -> packageService.createPackage(createCommand));
        verify(packageRepository, never()).save(any());
    }

    @Test
    void unknownSaleUnitCannotEnterPackageConfiguration() {
        when(productUnitService.requireEnabledUnit(1L, "袋", ProductUnitService.UnitOperation.SALE))
                .thenThrow(new IllegalArgumentException("单位未启用"));
        assertThrows(IllegalArgumentException.class, () -> packageService.createPackage(createCommand));
        verify(packageRepository, never()).save(any());
    }

    @Test
    void componentQuantityCannotBeRoundedFromMoreThanFourDecimals() {
        createCommand.getItems().get(0).setQuantity(new BigDecimal("0.00001"));
        assertThrows(IllegalArgumentException.class, () -> packageService.createPackage(createCommand));
        verify(packageRepository, never()).save(any());
    }

    @Test
    void duplicateProductsAreRejectedByRealDomainValidation() {
        org.springframework.test.util.ReflectionTestUtils.setField(packageService, "domainService",
                new ProductPackageDomainService(productRepository));
        createCommand.setItems(List.of(createCommand.getItems().get(0), createCommand.getItems().get(0)));
        assertThrows(IllegalArgumentException.class, () -> packageService.createPackage(createCommand));
        verify(packageRepository, never()).save(any());
    }
}

