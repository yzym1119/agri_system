package com.agri_system.api.admin.controller;

import com.agri_system.api.admin.config.TestApplication;
import com.agri_system.application.product.dto.command.PriceRuleCreateCommand;
import com.agri_system.application.product.dto.command.ProductUnitUpsertCommand;
import com.agri_system.application.product.dto.vo.ProductUnitVO;
import com.agri_system.application.product.service.ProductApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品控制器集成测试
 *
 * @author AI Generated
 */
@WebMvcTest
@ContextConfiguration(classes = {TestApplication.class, ProductController.class})
class ProductControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ProductApplicationService productService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testCreatePriceRule_StoreIdAndVipLevelCanBeNull() throws Exception {
        // Given: 创建价格规则，store_id和vip_level可以为空
        PriceRuleCreateCommand command = new PriceRuleCreateCommand();
        command.setProductId(1L);
        command.setStoreId(null); // 可以为空
        command.setVipLevel(null); // 可以为空
        command.setPriceType(2);
        command.setPrice(new BigDecimal("45.00"));
        
        when(productService.createPriceRule(any())).thenReturn(1L);
        
        // When & Then: 验证可以成功创建
        mockMvc.perform(post("/api/admin/products/price-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetProductUnitsReturnsConfiguredUnits() throws Exception {
        ProductUnitVO unit = new ProductUnitVO();
        unit.setUnitName("瓶");
        when(productService.getProductUnits(10L)).thenReturn(java.util.List.of(unit));

        mockMvc.perform(get("/api/admin/products/10/units"))
                .andExpect(status().isOk());
    }

    @Test
    void testReplaceProductUnitsAcceptsCompleteSet() throws Exception {
        ProductUnitUpsertCommand unit = new ProductUnitUpsertCommand();
        unit.setUnitName("瓶");
        unit.setBaseQuantityPerUnit(new BigDecimal("1"));
        unit.setIsBaseUnit(1);

        mockMvc.perform(put("/api/admin/products/10/units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.List.of(unit))))
                .andExpect(status().isOk());
    }
}










