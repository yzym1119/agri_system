package com.agri_system.infrastructure.customer.repository;

import com.agri_system.domain.customer.model.VipMonthlyRecord;
import com.agri_system.domain.customer.repository.VipMonthlyRecordRepository;
import com.agri_system.infrastructure.customer.mapper.VipMonthlyRecordMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * VIP每月权益发放记录仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class VipMonthlyRecordRepositoryImpl implements VipMonthlyRecordRepository {
    
    private final VipMonthlyRecordMapper mapper;
    
    @Override
    public VipMonthlyRecord save(VipMonthlyRecord record) {
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.update(record);
        }
        return record;
    }
    
    @Override
    public Optional<VipMonthlyRecord> findByCustomerIdAndYearMonth(Long customerId, String yearMonth) {
        VipMonthlyRecord record = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("customer_id = ?", customerId)
                        .and("`year_month` = ?", yearMonth)  // year_month是MySQL保留关键字，需要用反引号包裹
        );
        return Optional.ofNullable(record);
    }
    
    @Override
    public Page<VipMonthlyRecord> findPage(Integer pageNum, Integer pageSize, Long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .orderBy("grant_time DESC");
        Page<VipMonthlyRecord> page = new Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }
}







