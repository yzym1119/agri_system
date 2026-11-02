package com.agri_system.infrastructure.inventory.query;
import com.agri_system.domain.inventory.query.*;
import com.agri_system.infrastructure.inventory.mapper.InventoryPlanningMapper;
import com.mybatisflex.core.tenant.TenantManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class InventoryPlanningQueryImpl implements InventoryPlanningQuery {
    private final InventoryPlanningMapper mapper;
    @Override public Map<Long,PlanningStock> stocks(Long storeId) {
        Object[] tenants=TenantManager.getTenantIds();if(tenants!=null&&tenants.length==0)return Map.of();
        var result=new LinkedHashMap<Long,PlanningStock>();
        for(var row:mapper.stocks(storeId,tenants)) {
            Long id=id(value(row,"productId"));if(id==null)continue;
            result.put(id,new PlanningStock(string(value(row,"mainUnit")),decimal(value(row,"mainUnitFactor")),decimal(value(row,"currentStock"))));
        }
        return Collections.unmodifiableMap(result);
    }
    @Override public List<SalesVelocity> salesVelocity(int days,Long storeId) {
        if(days<=0)throw new IllegalArgumentException("观察窗口必须大于0");
        Object[] tenants=TenantManager.getTenantIds();if(tenants!=null&&tenants.length==0)return List.of();
        return mapper.salesVelocity(days,storeId,tenants).stream().map(row->new SalesVelocity(
            id(value(row,"productId")),string(value(row,"productName")),string(value(row,"productCode")),string(value(row,"mainUnit")),
            decimal(value(row,"avgDailySales")),decimal(value(row,"totalSold")),integer(value(row,"orderCount")),
            decimal(value(row,"currentMinStock")),decimal(value(row,"currentMaxStock")),decimal(value(row,"currentReorderQuantity")),
            integer(value(row,"unresolvedUnitCount"))==null?1:integer(value(row,"unresolvedUnitCount")))).toList();
    }
    private static Object value(Map<String,Object> row,String key) {for(var e:row.entrySet())if(e.getKey().equalsIgnoreCase(key))return e.getValue();return null;}
    private static BigDecimal decimal(Object value) {return value==null?null:value instanceof BigDecimal number?number:new BigDecimal(value.toString());}
    private static Long id(Object value) {return value==null?null:decimal(value).longValueExact();}
    private static Integer integer(Object value) {return value==null?null:decimal(value).intValueExact();}
    private static String string(Object value) {return value==null?null:value.toString();}
}
