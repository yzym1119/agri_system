package com.agri_system.domain.inventory.query;

import java.util.List;

/** 库存报表只读入口，null门店保留已有跨店报表语义；调用方继续负责权限与门店范围。 */
public interface InventoryReportQuery {
    List<InventoryReportBatch> findCurrentBatches(Long storeId);
}
