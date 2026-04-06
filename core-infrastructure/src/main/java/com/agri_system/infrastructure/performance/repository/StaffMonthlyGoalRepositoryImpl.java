package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffMonthlyGoal;
import com.agri_system.domain.performance.repository.StaffMonthlyGoalRepository;
import com.agri_system.infrastructure.performance.mapper.StaffMonthlyGoalMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StaffMonthlyGoalRepositoryImpl implements StaffMonthlyGoalRepository {

    private final StaffMonthlyGoalMapper mapper;

    @Override
    public StaffMonthlyGoal save(StaffMonthlyGoal goal) {
        if (goal.getId() == null) {
            mapper.insert(goal);
        } else {
            mapper.update(goal);
        }
        return goal;
    }

    @Override
    public Optional<StaffMonthlyGoal> findByStaffAndMonth(Long staffId, String month) {
        return Optional.ofNullable(mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("staff_id = ?", staffId)
                        .and("month = ?", month)));
    }
}

