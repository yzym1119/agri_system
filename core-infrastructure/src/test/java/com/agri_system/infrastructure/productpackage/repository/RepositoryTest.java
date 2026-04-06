package com.agri_system.infrastructure.productpackage.repository;

import com.agri_system.domain.productpackage.model.ProductPackage;
import com.agri_system.domain.productpackage.model.ProductPackageItem;
import com.agri_system.infrastructure.productpackage.mapper.ProductPackageItemMapper;
import com.agri_system.infrastructure.productpackage.mapper.ProductPackageMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 仓储层测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class RepositoryTest {
    
    @Mock
    private ProductPackageMapper packageMapper;
    
    @Mock
    private ProductPackageItemMapper itemMapper;
    
    @InjectMocks
    private ProductPackageRepositoryImpl packageRepository;
    
    @InjectMocks
    private ProductPackageItemRepositoryImpl itemRepository;
    
    private ProductPackage testPackage;
    private ProductPackageItem testItem1;
    private ProductPackageItem testItem2;
    
    @BeforeEach
    void setUp() {
        testPackage = new ProductPackage();
        testPackage.setId(1L);
        testPackage.setCode("PKG202401010001");
        testPackage.setName("测试套餐");
        testPackage.setStatus(1);
        
        testItem1 = new ProductPackageItem();
        testItem1.setId(1L);
        testItem1.setPackageId(1L);
        testItem1.setProductId(1L);
        testItem1.setQuantity(new BigDecimal("2.0000"));
        testItem1.setUnit("袋");
        
        testItem2 = new ProductPackageItem();
        testItem2.setId(2L);
        testItem2.setPackageId(1L);
        testItem2.setProductId(2L);
        testItem2.setQuantity(new BigDecimal("3.0000"));
        testItem2.setUnit("袋");
    }
    
    @Test
    void testFindItemsByPackageId_ShouldReturnMultipleItems() {
        // Given: 套餐有多个明细
        when(itemMapper.selectListByQuery(any())).thenReturn(Arrays.asList(testItem1, testItem2));
        
        // When: 查询明细
        List<ProductPackageItem> items = itemRepository.findItemsByPackageId(1L);
        
        // Then: 应返回多条
        assertNotNull(items);
        assertEquals(2, items.size());
        assertEquals(1L, items.get(0).getProductId());
        assertEquals(2L, items.get(1).getProductId());
    }
    
    @Test
    void testFindPage_WithQueryConditions_ShouldWork() {
        // Given: 分页查询条件
        when(packageMapper.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenAnswer(invocation -> {
            Page<ProductPackage> page = new Page<>();
            page.setRecords(Arrays.asList(testPackage));
            page.setPageNumber(1);
            page.setPageSize(10);
            page.setTotalRow(1);
            return page;
        });
        
        // When: 分页查询
        Page<ProductPackage> page = packageRepository.findPage("PKG", "测试", 1, 1, 10);
        
        // Then: 应正确返回分页结果
        assertNotNull(page);
        assertEquals(1, page.getRecords().size());
        assertEquals(1, page.getTotalRow());
    }
    
    @Test
    void testDeleteByPackageId_ShouldDeleteAllItems() {
        // Given: 套餐有明细
        doNothing().when(itemMapper).deleteByQuery(any());
        
        // When: 删除套餐的所有明细
        itemRepository.deleteByPackageId(1L);
        
        // Then: 应调用删除方法
        verify(itemMapper, times(1)).deleteByQuery(any());
    }
}


