package com.agri_system.domain.after_sales.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 售后领域服务
 *
 * @author AI
 */
public class AfterSalesDomainService {

    private final AtomicInteger sequence = new AtomicInteger(1);

    /**
     * 生成售后单号：AS + yyyyMMddHHmm + 4位自增
     */
    public String generateAfterSalesNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        int seq = sequence.getAndIncrement();
        if (seq > 9999) {
            sequence.set(1);
            seq = 1;
        }
        return "AS" + timePart + String.format("%04d", seq);
    }
}








