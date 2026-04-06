package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.StaffSalaryBill;

import java.util.List;
import java.util.Optional;

public interface StaffSalaryBillRepository {

    StaffSalaryBill save(StaffSalaryBill bill);

    Optional<StaffSalaryBill> findByStaffAndMonth(Long staffId, String month);

    List<StaffSalaryBill> query(String month, Integer status, int page, int size);

    Optional<StaffSalaryBill> findById(Long id);
}

