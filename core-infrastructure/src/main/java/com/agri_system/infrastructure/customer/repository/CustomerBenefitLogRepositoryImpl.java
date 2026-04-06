package com.agri_system.infrastructure.customer.repository;

import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.infrastructure.customer.mapper.CustomerBenefitLogMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 客户权益流水仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CustomerBenefitLogRepositoryImpl implements CustomerBenefitLogRepository {
    
    private final CustomerBenefitLogMapper mapper;
    
    @Override
    public CustomerBenefitLog save(CustomerBenefitLog log) {
        if (log.getId() == null) {
            mapper.insert(log);
        } else {
            mapper.update(log);
        }
        return log;
    }
    
    @Override
    public Page<CustomerBenefitLog> findPage(Integer pageNum, Integer pageSize, Long customerId) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId)
                .orderBy("created_at DESC");
        Page<CustomerBenefitLog> page = new Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }
}









