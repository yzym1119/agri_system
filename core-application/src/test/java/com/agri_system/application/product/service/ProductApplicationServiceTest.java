package com.agri_system.application.product.service;

import com.agri_system.application.product.dto.command.ProductCreateCommand;
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
}











