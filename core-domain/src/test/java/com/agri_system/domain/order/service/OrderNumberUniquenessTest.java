package com.agri_system.domain.order.service;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/** 固定同一时刻的密集开单不能耗尽一个很小的随机编号空间。 */
class OrderNumberUniquenessTest {
    @Test
    void sameSecondAcrossServiceInstancesDoesNotRepeatOrderNumbers() {
        LocalDateTime sameInstant=LocalDateTime.of(2026,10,16,9,0);
        OrderDomainService first=new OrderDomainService(),second=new OrderDomainService();
        Set<String> numbers=new HashSet<>();
        try (MockedStatic<LocalDateTime> time=mockStatic(LocalDateTime.class)) {
            time.when(LocalDateTime::now).thenReturn(sameInstant);
            for(int index=0;index<10_001;index++) {
                String number=(index%2==0 ? first : second).generateOrderNo();
                assertTrue(number.length()<=50,"订单号须适配现有数据库字段，无需迁移");
                assertTrue(numbers.add(number),"同一时刻的新订单不能共用编号，重复编号："+number);
            }
        }
    }
}
