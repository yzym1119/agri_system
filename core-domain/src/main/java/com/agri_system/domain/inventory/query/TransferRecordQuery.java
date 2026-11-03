package com.agri_system.domain.inventory.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 调拨历史专用只读事实；实现必须将真实租户与显式筛选取交集。 */
public interface TransferRecordQuery {
    record Criteria(Integer pageNum,Integer pageSize,Long fromStoreId,Long toStoreId,String productName,String startTime,String endTime) {}
    record Row(Long transferId, String transferNo, Long fromStoreId, String fromStoreName, Long toStoreId, String toStoreName, Long productId, String productName, String productSpec, BigDecimal quantity, Integer batchCount, Long createdBy, String createdByName, LocalDateTime createdAt, String remark) {}
    record Result(long totalRows,List<Row> rows) {
        public Result { rows=List.copyOf(rows); }
    }
    Result find(Criteria query);
}
