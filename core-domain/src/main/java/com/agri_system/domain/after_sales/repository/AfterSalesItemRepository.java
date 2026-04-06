package com.agri_system.domain.after_sales.repository;

import com.agri_system.domain.after_sales.model.AfterSalesItem;

import java.util.List;

/**
 * 售后明细仓储
 *
 * @author AI
 */
public interface AfterSalesItemRepository {

    void saveBatch(List<AfterSalesItem> items);

    List<AfterSalesItem> findByAfterSalesId(Long afterSalesId);
}








