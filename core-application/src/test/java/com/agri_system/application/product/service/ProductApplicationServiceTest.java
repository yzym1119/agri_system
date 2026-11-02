package com.agri_system.application.product.service;

import com.agri_system.application.product.dto.command.ProductCreateCommand;
import com.agri_system.application.product.dto.command.ProductUpdateCommand;
import com.agri_system.domain.product.model.Product;
import com.agri_system.domain.product.repository.ProductRepository;
import com.agri_system.domain.product.service.ProductDomainService;
import com.agri_system.application.support.SaTokenTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 商品应用服务单元测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private ProductDomainService domainService;
    
    @Mock
    private ProductUnitService productUnitService;
    @Mock
    private com.agri_system.domain.inventory.repository.InventoryRepository inventoryRepository;
    @Mock
    private com.agri_system.domain.order.repository.OrderItemRepository orderItemRepository;
    @Mock
    private com.agri_system.domain.productpackage.repository.ProductPackageItemRepository productPackageItemRepository;
    @Mock
    private com.agri_system.domain.purchase.repository.PurchaseItemRepository purchaseItemRepository;

    @InjectMocks
    private ProductApplicationService productService;
    
    private ProductCreateCommand createCommand;
    
    @BeforeEach
    void setUp() {
        SaTokenTestContext.setUp();
        createCommand = new ProductCreateCommand();
        createCommand.setCode("P001");
        createCommand.setName("测试商品");
        createCommand.setMainUnit("袋");
        createCommand.setSuggestedRetailPrice(new BigDecimal("50.00"));
    }

    @AfterEach
    void tearDown() {
        SaTokenTestContext.tearDown();
    }

    @Test
    void queryProductsUsesStatusBeforeRepositoryPagination() {
        var query=new com.agri_system.application.product.dto.query.ProductQuery();
        query.setStatus(1);query.setPageSize(1);
        var page=new com.mybatisflex.core.paginate.Page<Product>();page.setRecords(java.util.List.of());
        when(productRepository.findPage(null,null,null,null,1,null,null,1,1)).thenReturn(page);
        var result=productService.queryProducts(query);
        org.junit.jupiter.api.Assertions.assertTrue(result.getRecords().isEmpty());
        verify(productRepository).findPage(null,null,null,null,1,null,null,1,1);
    }

    @Test
    void testCreateProduct_VerifyAvgCostPriceNotSet() {
        // Given: 商品编码不存在
        when(productRepository.findByCode("P001")).thenReturn(Optional.empty());
        // createProduct 返回 product.getId()；单测里 save 是 mock，用 doAnswer 回填 id
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productRepository).save(any(Product.class));

        // When: 创建商品
        Long productId = productService.createProduct(createCommand);
        
        // Then: 验证avg_cost_price未被赋值（应为null或0）
        verify(productRepository, times(1)).save(argThat(product -> {
            Product p = (Product) product;
            return p.getAvgCostPrice() == null || 
                   p.getAvgCostPrice().compareTo(BigDecimal.ZERO) == 0;
        }));
        assertNotNull(productId);
        verify(productUnitService).initializeUnits(any(Product.class));
    }
    
    @Test
    void deleteUsesCurrentStockSnapshot() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(new Product()));
        when(inventoryRepository.sumQuantityByProduct(1L, null)).thenReturn(BigDecimal.ONE);
        assertThrows(IllegalStateException.class, () -> productService.deleteProduct(1L));
        verify(productRepository, never()).delete(1L);
        when(inventoryRepository.sumQuantityByProduct(1L, null)).thenReturn(BigDecimal.ZERO);
        productService.deleteProduct(1L);
        verify(productRepository).delete(1L);
    }

    @Test
    void cannotRedefineUnitConversionThroughProductProfile() {
        Product product = new Product();
        product.setId(1L);
        product.setMainUnit("箱");
        product.setAuxUnit("瓶");
        product.setMainToAuxRatio(new BigDecimal("20"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        ProductUpdateCommand update = new ProductUpdateCommand();
        update.setId(1L);
        update.setMainToAuxRatio(new BigDecimal("24"));
        assertThrows(IllegalArgumentException.class, () -> productService.updateProduct(update));
        verify(productRepository, never()).save(any());
    }

    @Test
    void testCreateProduct_CodeExists_ThrowsException() {
        // Given: 商品编码已存在
        Product existingProduct = new Product();
        existingProduct.setId(1L);
        existingProduct.setCode("P001");
        when(productRepository.findByCode("P001")).thenReturn(Optional.of(existingProduct));
        
        // When & Then: 创建商品应抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            productService.createProduct(createCommand);
        });
    }
    @Test
    void cannotCreateAnotherWritablePriceStrategy() {
        var command = new com.agri_system.application.product.dto.command.PriceRuleCreateCommand();
        command.setProductId(1L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> productService.createPriceRule(command))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("商品档案");
    }

    @Test
    void rejectsVipPriceAboveRetail() {
        createCommand.setVipPrice(new BigDecimal("60"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> productService.createProduct(createCommand))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("会员价");
    }

    @Test
    void rejectsFloorAboveRetail() {
        createCommand.setMinPrice(new BigDecimal("60"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> productService.createProduct(createCommand))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最低价");
    }

    @Test
    void configuredMemberPriceCannotBeBelowFloor() {
        createCommand.setVipPrice(new BigDecimal("40"));
        createCommand.setMinPrice(new BigDecimal("45"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> productService.createProduct(createCommand))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("会员价");
    }

}











