package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.StaffMonthlyGoal;

import java.util.List;
import java.util.Optional;

public interface StaffMonthlyGoalRepository {

    StaffMonthlyGoal save(StaffMonthlyGoal goal);

    Optional<StaffMonthlyGoal> findByStaffAndMonth(Long staffId, String month);

    List<StaffMonthlyGoal> query(Long staffId, String monthFrom, String monthTo, int page, int size);

    long count(Long staffId, String monthFrom, String monthTo);
}

