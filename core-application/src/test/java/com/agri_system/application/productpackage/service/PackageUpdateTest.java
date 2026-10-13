package com.agri_system.application.productpackage.service;

import com.agri_system.application.productpackage.dto.command.PackageItemDTO;
import com.agri_system.application.productpackage.dto.command.ProductPackageUpdateCommand;
import com.agri_system.application.product.service.ProductUnitService;
import com.agri_system.domain.product.model.ProductUnit;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
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

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 套餐更新测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PackageUpdateTest {
    
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
    
    private ProductPackageUpdateCommand updateCommand;
    private ProductPackage existingPackage;
    private Product testProduct1;
    private Product testProduct2;
    
    @BeforeEach
    void setUp() {
        updateCommand = new ProductPackageUpdateCommand();
        updateCommand.setId(1L);
        updateCommand.setName("更新后的套餐");
        updateCommand.setSuggestedRetailPrice(new BigDecimal("250.00"));
        updateCommand.setVipPrice(new BigDecimal("220.00"));
        updateCommand.setMinPrice(new BigDecimal("200.00"));
        
        existingPackage = new ProductPackage();
        existingPackage.setId(1L);
        existingPackage.setCode("PKG202401010001");
        existingPackage.setName("原套餐");
        existingPackage.setStatus(0);
        existingPackage.setSuggestedRetailPrice(new BigDecimal("200.00"));
        when(packageRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingPackage));
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existingPackage));
        when(productUnitService.requireEnabledUnit(anyLong(), anyString(), eq(ProductUnitService.UnitOperation.SALE)))
                .thenAnswer(inv -> {
                    ProductUnit unit = new ProductUnit(); unit.setUnitName(inv.getArgument(1));
                    unit.setBaseQuantityPerUnit(BigDecimal.ONE); return unit;
                });
        
        testProduct1 = new Product();
        testProduct1.setId(1L);
        testProduct1.setName("商品1");
        testProduct1.setStatus(1);
        
        testProduct2 = new Product();
        testProduct2.setId(2L);
        testProduct2.setName("商品2");
        testProduct2.setStatus(1);
    }
    
    @Test
    void testUpdatePackage_FullReplaceItems_ShouldDeleteOldAndAddNew() {
        // Given: 原有明细包含商品1，新明细包含商品2
        List<ProductPackageItem> oldItems = new ArrayList<>();
        ProductPackageItem oldItem = new ProductPackageItem();
        oldItem.setId(1L);
        oldItem.setPackageId(1L);
        oldItem.setProductId(1L);
        oldItems.add(oldItem);
        
        List<PackageItemDTO> newItems = new ArrayList<>();
        PackageItemDTO newItem = new PackageItemDTO();
        newItem.setProductId(2L);
        newItem.setQuantity(new BigDecimal("3.0000"));
        newItem.setUnit("袋");
        newItems.add(newItem);
        updateCommand.setItems(newItems);
        
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existingPackage));
        when(itemRepository.findItemsByPackageId(1L)).thenReturn(oldItems);
        when(productRepository.findById(2L)).thenReturn(Optional.of(testProduct2));
        
        // When: 更新套餐
        packageService.updatePackage(updateCommand);
        
        // Then: 应删除旧明细，添加新明细
        verify(itemRepository, times(1)).deleteByPackageId(1L);
        verify(itemRepository, times(1)).saveBatch(argThat(items -> {
            List<ProductPackageItem> itemList = (List<ProductPackageItem>) items;
            return itemList.size() == 1 && 
                   itemList.get(0).getProductId().equals(2L) &&
                   itemList.get(0).getQuantity().compareTo(new BigDecimal("3.0000")) == 0;
        }));
        verify(packageRepository, times(1)).save(argThat(pkg -> 
            pkg.getName().equals("更新后的套餐") &&
            pkg.getSuggestedRetailPrice().compareTo(new BigDecimal("250.00")) == 0
        ));
    }
    
    @Test
    void testUpdatePackage_UpdateQuantity_ShouldWork() {
        // Given: 更新数量
        List<PackageItemDTO> newItems = new ArrayList<>();
        PackageItemDTO item = new PackageItemDTO();
        item.setProductId(1L);
        item.setQuantity(new BigDecimal("5.0000")); // 从2.0000更新为5.0000
        item.setUnit("袋");
        newItems.add(item);
        updateCommand.setItems(newItems);
        
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existingPackage));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        
        // When: 更新套餐
        packageService.updatePackage(updateCommand);
        
        // Then: 应正确更新数量
        verify(itemRepository, times(1)).saveBatch(argThat(items -> {
            List<ProductPackageItem> itemList = (List<ProductPackageItem>) items;
            return itemList.size() == 1 && 
                   itemList.get(0).getQuantity().compareTo(new BigDecimal("5.0000")) == 0;
        }));
    }
    
    @Test
    void testUpdatePackage_AddNewItems_ShouldWork() {
        // Given: 添加新商品
        List<PackageItemDTO> newItems = new ArrayList<>();
        PackageItemDTO item1 = new PackageItemDTO();
        item1.setProductId(1L);
        item1.setQuantity(new BigDecimal("2.0000"));
        item1.setUnit("袋");
        newItems.add(item1);
        
        PackageItemDTO item2 = new PackageItemDTO();
        item2.setProductId(2L);
        item2.setQuantity(new BigDecimal("3.0000"));
        item2.setUnit("袋");
        newItems.add(item2);
        updateCommand.setItems(newItems);
        
        when(packageRepository.findById(1L)).thenReturn(Optional.of(existingPackage));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(testProduct2));
        
        // When: 更新套餐
        packageService.updatePackage(updateCommand);
        
        // Then: 应添加新明细
        verify(itemRepository, times(1)).saveBatch(argThat(items -> {
            List<ProductPackageItem> itemList = (List<ProductPackageItem>) items;
            return itemList.size() == 2;
        }));
    }
    @Test
    void invalidUpdateCannotDeleteExistingComposition() {
        updateCommand.setVipPrice(new BigDecimal("190.00"));
        updateCommand.setItems(List.of(component()));
        assertThrows(IllegalArgumentException.class, () -> packageService.updatePackage(updateCommand));
        verify(itemRepository, never()).deleteByPackageId(1L);
    }

    @Test
    void disabledUnitPreventsUpdatingComposition() {
        updateCommand.setItems(List.of(component()));
        when(productUnitService.requireEnabledUnit(1L, "袋", ProductUnitService.UnitOperation.SALE))
                .thenThrow(new IllegalArgumentException("单位停用"));
        assertThrows(IllegalArgumentException.class, () -> packageService.updatePackage(updateCommand));
        verify(itemRepository, never()).deleteByPackageId(1L);
    }

    @Test
    void lockedCurrentPackageStateControlsUpdate() {
        ProductPackage deleted = new ProductPackage(); deleted.setStatus(-1);
        when(packageRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deleted));
        updateCommand.setItems(List.of(component()));
        assertThrows(IllegalStateException.class, () -> packageService.updatePackage(updateCommand));
        verify(itemRepository, never()).deleteByPackageId(1L);
    }

    @Test
    void publishingRequiresPositiveCurrentPrice() {
        existingPackage.setSuggestedRetailPrice(BigDecimal.ZERO);
        when(itemRepository.findItemsByPackageId(1L)).thenReturn(List.of(storedComponent()));
        assertThrows(IllegalArgumentException.class, () -> packageService.updateStatus(1L, 1));
        verify(packageRepository, never()).updateStatus(1L, 1);
    }

    @Test
    void publishingRechecksSalesUnitAfterItWasDisabled() {
        when(itemRepository.findItemsByPackageId(1L)).thenReturn(List.of(storedComponent()));
        when(productUnitService.requireEnabledUnit(1L, "袋", ProductUnitService.UnitOperation.SALE))
                .thenThrow(new IllegalArgumentException("单位停用"));
        assertThrows(IllegalArgumentException.class, () -> packageService.updateStatus(1L, 1));
        verify(packageRepository, never()).updateStatus(1L, 1);
    }

    private PackageItemDTO component() {
        PackageItemDTO item = new PackageItemDTO(); item.setProductId(1L);
        item.setQuantity(BigDecimal.ONE); item.setUnit("袋"); return item;
    }

    private ProductPackageItem storedComponent() {
        ProductPackageItem item = new ProductPackageItem(); item.setProductId(1L);
        item.setQuantity(BigDecimal.ONE); item.setUnit("袋"); return item;
    }
}










