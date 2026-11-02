package com.agri_system.infrastructure.customer.query;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.query.CustomerReportQuery;
import com.agri_system.domain.customer.query.CustomerReportRow;
import com.agri_system.domain.order.model.Order;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.tenant.TenantManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 共享客户目录不带门店字段；门店分析必须经原订单范围与当前Flex租户相交。 */
@Repository
@RequiredArgsConstructor
public class CustomerReportQueryImpl implements CustomerReportQuery {
    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;

    @Override public Long countCreatedBetween(LocalDateTime start, LocalDateTime end) {
        var query=QueryWrapper.create().where("created_at between ? and ?",start,end);
        return customerMapper.selectOneByQueryAs(query.select("COUNT(1) AS cnt"),Long.class);
    }
    @Override public List<CustomerReportRow> rankedCustomers(int limit, Long storeId) {
        var query=QueryWrapper.create().where("total_order_amount is not null");
        if(!applyStoreScope(query,storeId))return List.of();
        query.orderBy("total_order_amount desc").limit(limit);
        return customerMapper.selectListByQuery(query).stream().map(this::row).toList();
    }
    @Override public List<CustomerReportRow> churnCandidates(Long storeId, LocalDateTime deadline) {
        var query=QueryWrapper.create().where("last_order_time is not null")
                .and("last_order_time < ?",deadline).and("total_order_amount > 1000");
        if(!applyStoreScope(query,storeId))return List.of();
        return customerMapper.selectListByQuery(query).stream().map(this::row).toList();
    }
    private boolean applyStoreScope(QueryWrapper customerQuery, Long storeId) {
        Object[] tenants=TenantManager.getTenantIds();
        if(tenants!=null&&tenants.length==0)return false;
        if(storeId==null&&tenants==null)return true; // 管理员未指定门店，保留全店排行。
        var orders=QueryWrapper.create().select("DISTINCT customer_id").where("customer_id is not null");
        if(storeId!=null)orders.and("store_id = ?",storeId);
        // 此Mapper对订单自动附加真实租户与逻辑删除条件，未传门店也不能绕过。
        List<Long> ids=orderMapper.selectListByQuery(orders).stream().map(Order::getCustomerId)
                .filter(id->id!=null).distinct().toList();
        if(ids.isEmpty())return false;
        customerQuery.and("id in ("+ids.stream().map(id->"?").collect(Collectors.joining(","))+")",ids.toArray());
        return true;
    }
    private CustomerReportRow row(Customer customer) {
        return new CustomerReportRow(customer.getId(),customer.getName(),customer.getPhone(),customer.getTotalOrderAmount(),customer.getLastOrderTime());
    }
}
