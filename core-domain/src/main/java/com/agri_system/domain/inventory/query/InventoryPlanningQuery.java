package com.agri_system.domain.inventory.query;
import java.util.List;
import java.util.Map;
/** 统一主单位规划证据读取，门店参数必须与真实租户相交。无写入或自动补货能力。 */
public interface InventoryPlanningQuery {
    Map<Long,PlanningStock> stocks(Long storeId);
    List<SalesVelocity> salesVelocity(int days,Long storeId);
}
