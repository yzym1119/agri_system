package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.StaffMonthlyGoal;

import java.util.Optional;

public interface StaffMonthlyGoalRepository {

    StaffMonthlyGoal save(StaffMonthlyGoal goal);

    Optional<StaffMonthlyGoal> findByStaffAndMonth(Long staffId, String month);
}

