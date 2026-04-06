package com.agri_system.domain.after_sales.repository;

import com.agri_system.domain.after_sales.model.AfterSales;

import java.util.List;
import java.util.Optional;

/**
 * 售后仓储
 *
 * @author AI
 */
public interface AfterSalesRepository {

    AfterSales save(AfterSales afterSales);

    Optional<AfterSales> findById(Long id);

    Optional<AfterSales> findProcessingByOrderId(Long orderId);

    List<AfterSales> findByOrderId(Long orderId);
}








