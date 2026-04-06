package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.StaffSalaryBill;
import com.agri_system.domain.performance.repository.StaffSalaryBillRepository;
import com.agri_system.infrastructure.performance.mapper.StaffSalaryBillMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StaffSalaryBillRepositoryImpl implements StaffSalaryBillRepository {

    private final StaffSalaryBillMapper mapper;

    @Override
    public StaffSalaryBill save(StaffSalaryBill bill) {
        if (bill.getId() == null) {
            mapper.insert(bill);
        } else {
            mapper.update(bill);
        }
        return bill;
    }

    @Override
    public Optional<StaffSalaryBill> findByStaffAndMonth(Long staffId, String month) {
        return Optional.ofNullable(mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("staff_id = ?", staffId)
                        .and("month = ?", month)));
    }

    @Override
    public List<StaffSalaryBill> query(String month, Integer status, int page, int size) {
        QueryWrapper query = QueryWrapper.create();
        if (month != null) {
            query.where("month = ?", month);
        }
        if (status != null) {
            query.and("status = ?", status);
        }
        query.orderBy("created_at DESC")
                .limit((page - 1) * size, size);
        return mapper.selectListByQuery(query);
    }

    @Override
    public Optional<StaffSalaryBill> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }
}

