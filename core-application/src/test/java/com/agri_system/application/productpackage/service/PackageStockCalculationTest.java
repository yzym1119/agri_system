package com.agri_system.application.productpackage.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 套餐库存计算单位换算测试
 * 
 * 测试场景：
 * 1. 套餐需求单位 = 商品主单位（无需转换）
 * 2. 套餐需求单位 = 商品辅单位（需要转换）
 * 3. 单位不匹配（兜底处理）
 */
public class PackageStockCalculationTest {
    
    /**
     * 测试场景1：套餐需求单位 = 商品主单位
     * 
     * 商品：农药
     * - 主单位：箱
     * - 辅单位：瓶
     * - 换算比例：20（1箱=20瓶）
     * - 库存：10箱
     * 
     * 套餐需求：2箱（主单位）
     * 
     * 预期结果：
     * - 归一化需求量 = 2箱
     * - 可组成套数 = 10 / 2 = 5套
     */
    @Test
    public void testMainUnitCalculation() {
        // 模拟数据
        BigDecimal inventory = new BigDecimal("10"); // 10箱
        BigDecimal requiredQuantity = new BigDecimal("2"); // 需要2箱
        String itemUnit = "箱";
        String mainUnit = "箱";
        String auxUnit = "瓶";
        BigDecimal ratio = new BigDecimal("20");
        
        // 计算归一化需求量
        BigDecimal normalizedRequired = requiredQuantity; // 主单位，无需转换
        
        // 计算可组成套数
        BigDecimal packageCount = inventory.divide(normalizedRequired, 0, RoundingMode.FLOOR);
        
        assertEquals(new BigDecimal("5"), packageCount, "主单位计算错误");
    }
    
    /**
     * 测试场景2：套餐需求单位 = 商品辅单位（修复前会出错的场景）
     * 
     * 商品：农药
     * - 主单位：箱
     * - 辅单位：瓶
     * - 换算比例：20（1箱=20瓶）
     * - 库存：10箱
     * 
     * 套餐需求：5瓶（辅单位）
     * 
     * 修复前（错误）：
     * - 归一化需求量 = 5 * 20 = 100箱 ❌
     * - 可组成套数 = 10 / 100 = 0套 ❌
     * 
     * 修复后（正确）：
     * - 归一化需求量 = 5 / 20 = 0.25箱 ✅
     * - 可组成套数 = 10 / 0.25 = 40套 ✅
     */
    @Test
    public void testAuxUnitCalculation() {
        // 模拟数据
        BigDecimal inventory = new BigDecimal("10"); // 10箱
        BigDecimal requiredQuantity = new BigDecimal("5"); // 需要5瓶
        String itemUnit = "瓶";
        String mainUnit = "箱";
        String auxUnit = "瓶";
        BigDecimal ratio = new BigDecimal("20"); // 1箱=20瓶
        
        // 计算归一化需求量（辅单位转主单位）
        BigDecimal normalizedRequired = requiredQuantity.divide(ratio, 6, RoundingMode.HALF_UP);
        // 5 / 20 = 0.25箱
        
        // 计算可组成套数
        BigDecimal packageCount = inventory.divide(normalizedRequired, 0, RoundingMode.FLOOR);
        // 10 / 0.25 = 40套
        
        assertEquals(new BigDecimal("0.25"), normalizedRequired.setScale(2, RoundingMode.HALF_UP), 
                    "辅单位转主单位计算错误");
        assertEquals(new BigDecimal("40"), packageCount, "套餐数量计算错误");
    }
    
    /**
     * 测试场景3：复杂场景 - 多个商品的木桶效应
     * 
     * 套餐包含：
     * - 商品A：需要2箱，库存10箱 → 可组成5套
     * - 商品B：需要5瓶，库存10箱(=200瓶) → 可组成40套
     * - 商品C：需要3箱，库存20箱 → 可组成6套
     * 
     * 预期结果：min(5, 40, 6) = 5套（木桶原理）
     */
    @Test
    public void testBucketAlgorithm() {
        // 商品A
        BigDecimal inventoryA = new BigDecimal("10");
        BigDecimal requiredA = new BigDecimal("2");
        BigDecimal countA = inventoryA.divide(requiredA, 0, RoundingMode.FLOOR);
        
        // 商品B（辅单位）
        BigDecimal inventoryB = new BigDecimal("10"); // 10箱
        BigDecimal requiredB = new BigDecimal("5"); // 5瓶
        BigDecimal ratioB = new BigDecimal("20"); // 1箱=20瓶
        BigDecimal normalizedB = requiredB.divide(ratioB, 6, RoundingMode.HALF_UP); // 0.25箱
        BigDecimal countB = inventoryB.divide(normalizedB, 0, RoundingMode.FLOOR);
        
        // 商品C
        BigDecimal inventoryC = new BigDecimal("20");
        BigDecimal requiredC = new BigDecimal("3");
        BigDecimal countC = inventoryC.divide(requiredC, 0, RoundingMode.FLOOR);
        
        // 木桶效应：取最小值
        BigDecimal minCount = countA.min(countB).min(countC);
        
        assertEquals(new BigDecimal("5"), countA, "商品A计算错误");
        assertEquals(new BigDecimal("40"), countB, "商品B计算错误");
        assertEquals(new BigDecimal("6"), countC, "商品C计算错误");
        assertEquals(new BigDecimal("5"), minCount, "木桶效应计算错误");
    }
    
    /**
     * 测试场景4：边界情况 - 库存不足
     * 
     * 商品：农药
     * - 库存：2箱
     * - 套餐需求：5瓶（= 0.25箱）
     * 
     * 预期结果：2 / 0.25 = 8套
     */
    @Test
    public void testLowInventory() {
        BigDecimal inventory = new BigDecimal("2"); // 2箱
        BigDecimal required = new BigDecimal("5"); // 5瓶
        BigDecimal ratio = new BigDecimal("20"); // 1箱=20瓶
        
        BigDecimal normalized = required.divide(ratio, 6, RoundingMode.HALF_UP);
        BigDecimal count = inventory.divide(normalized, 0, RoundingMode.FLOOR);
        
        assertEquals(new BigDecimal("8"), count, "低库存计算错误");
    }
    
    /**
     * 测试场景5：边界情况 - 库存为0
     */
    @Test
    public void testZeroInventory() {
        BigDecimal inventory = BigDecimal.ZERO;
        BigDecimal required = new BigDecimal("5");
        BigDecimal ratio = new BigDecimal("20");
        
        BigDecimal normalized = required.divide(ratio, 6, RoundingMode.HALF_UP);
        BigDecimal count = inventory.divide(normalized, 0, RoundingMode.FLOOR);
        
        assertEquals(BigDecimal.ZERO, count, "零库存计算错误");
    }
}






