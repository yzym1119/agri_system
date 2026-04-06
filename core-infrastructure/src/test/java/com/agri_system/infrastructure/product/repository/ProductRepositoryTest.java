package com.agri_system.infrastructure.product.repository;

import com.agri_system.domain.product.model.Product;
import com.agri_system.infrastructure.product.mapper.ProductMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 商品仓储单元测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {
    
    @Mock
    private ProductMapper productMapper;
    
    @InjectMocks
    private ProductRepositoryImpl productRepository;
    
    private Product testProduct;
    
    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setCode("P001");
        testProduct.setName("测试商品");
        testProduct.setCategoryId(1L);
    }
    
    @Test
    void testFindById() {
        // Given
        when(productMapper.selectOneById(1L)).thenReturn(testProduct);
        
        // When
        Optional<Product> result = productRepository.findById(1L);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("P001", result.get().getCode());
    }
    
    @Test
    void testFindPage_WithCategoryFilter() {
        // Given
        Page<Product> mockPage = new Page<>();
        mockPage.setPageNumber(1);
        mockPage.setPageSize(10);
        mockPage.setTotalRow(1);
        mockPage.setRecords(java.util.Arrays.asList(testProduct));
        
        when(productMapper.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenReturn(mockPage);
        
        // When: 按分类ID筛选
        Page<Product> result = productRepository.findPage(
                null, null, 1L, null, null, null, 1, 10
        );
        
        // Then: 验证分类筛选有效
        assertNotNull(result);
        assertEquals(1, result.getTotalRow());
        verify(productMapper, times(1)).paginate(eq(1), eq(10), any(QueryWrapper.class));
    }
}



