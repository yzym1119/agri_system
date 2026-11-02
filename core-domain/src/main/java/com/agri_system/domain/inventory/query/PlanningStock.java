package com.agri_system.domain.inventory.query;
import java.math.BigDecimal;
/** 规划/预警用主单位可用量；quantity为空表示证据或换算无法核对，不能当零。 */
public record PlanningStock(String mainUnit, BigDecimal basePerMainUnit, BigDecimal quantity) { }
