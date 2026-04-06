package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffCustomerRelation;
import com.agri_system.domain.performance.repository.StaffCustomerRelationRepository;
import com.agri_system.infrastructure.performance.mapper.StaffCustomerRelationMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StaffCustomerRelationRepositoryImpl implements StaffCustomerRelationRepository {

    private final StaffCustomerRelationMapper mapper;

    @Override
    public StaffCustomerRelation save(StaffCustomerRelation relation) {
        if (relation.getId() == null) {
            mapper.insert(relation);
        } else {
            mapper.update(relation);
        }
        return relation;
    }

    @Override
    public Optional<StaffCustomerRelation> findByStaffAndCustomer(Long staffId, Long customerId) {
        return Optional.ofNullable(mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("staff_id = ?", staffId)
                        .and("customer_id = ?", customerId)));
    }

    @Override
    public Optional<StaffCustomerRelation> findByCustomer(Long customerId) {
        return Optional.ofNullable(mapper.selectOneByQuery(
                QueryWrapper.create().where("customer_id = ?", customerId)));
    }

    @Override
    public long countFirstEffectiveByMonth(Long staffId, String month) {
        return mapper.selectCountByQuery(QueryWrapper.create()
                .where("staff_id = ?", staffId)
                .and("first_effective_at IS NOT NULL")
                .and("DATE_FORMAT(first_effective_at,'%Y-%m') = ?", month));
    }
}

