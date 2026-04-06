package com.agri_system.domain.performance.repository;

import com.agri_system.domain.performance.model.StaffCustomerRelation;

import java.util.Optional;

public interface StaffCustomerRelationRepository {

    StaffCustomerRelation save(StaffCustomerRelation relation);

    Optional<StaffCustomerRelation> findByStaffAndCustomer(Long staffId, Long customerId);

    Optional<StaffCustomerRelation> findByCustomer(Long customerId);

    long countFirstEffectiveByMonth(Long staffId, String month);
}

