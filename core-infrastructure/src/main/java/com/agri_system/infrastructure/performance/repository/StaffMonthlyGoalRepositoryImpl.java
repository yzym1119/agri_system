package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffMonthlyGoal;
import com.agri_system.domain.performance.repository.StaffMonthlyGoalRepository;
import com.agri_system.infrastructure.performance.mapper.StaffMonthlyGoalMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Override
    public List<StaffMonthlyGoal> query(Long staffId, String monthFrom, String monthTo, int page, int size) {
        QueryWrapper query = buildGoalQuery(staffId, monthFrom, monthTo)
                .orderBy("month", false)
                .limit((page - 1) * size, size);
        return mapper.selectListByQuery(query);
    }

    @Override
    public long count(Long staffId, String monthFrom, String monthTo) {
        return mapper.selectCountByQuery(buildGoalQuery(staffId, monthFrom, monthTo));
    }

    private QueryWrapper buildGoalQuery(Long staffId, String monthFrom, String monthTo) {
        QueryWrapper query = QueryWrapper.create();
        if (staffId != null) {
            query.where("staff_id = ?", staffId);
        }
        if (monthFrom != null && !monthFrom.isEmpty()) {
            query.and("month >= ?", monthFrom);
        }
        if (monthTo != null && !monthTo.isEmpty()) {
            query.and("month <= ?", monthTo);
        }
        return query;
    }
}

