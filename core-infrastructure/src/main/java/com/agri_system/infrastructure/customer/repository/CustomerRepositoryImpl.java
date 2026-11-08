package com.agri_system.infrastructure.customer.repository;

import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 客户仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {
    
    private final CustomerMapper mapper;
    
    @Override
    public Customer save(Customer customer) {
        if (customer.getId() == null) {
            mapper.insert(customer);
        } else {
            mapper.update(customer);
        }
        return customer;
    }
    
    @Override
    public Optional<Customer> findById(Long id) {
        QueryWrapper query = QueryWrapper.create()
                .where("id = ?", id)
                .and("deleted = 0");
        return Optional.ofNullable(mapper.selectOneByQuery(query));
    }

    @Override
    public Optional<Customer> findByIdIncludingDeleted(Long id) {
        return Optional.ofNullable(mapper.selectByIdIncludingDeleted(id));
    }
    
    @Override
    public Optional<Customer> findByIdWithLock(Long id) {
        Customer customer = mapper.selectByIdWithLock(id);
        return Optional.ofNullable(customer);
    }

    @Override
    public List<Customer> findAllVipCustomers() {
        QueryWrapper query = QueryWrapper.create()
                .where("type = ?", 2); // VIP客户
        return mapper.selectListByQuery(query);
    }
    
    @Override
    public Page<Customer> findPage(Integer pageNum, Integer pageSize, 
                                  String name, String phone, 
                                  Integer type, Integer status, Long bindStaffId) {
        QueryWrapper query = QueryWrapper.create();
        
        if (StringUtils.hasText(name)) {
            query.where("name LIKE ?", "%" + name + "%");
        }
        if (StringUtils.hasText(phone)) {
            query.and("phone LIKE ?", "%" + phone + "%");
        }
        if (type != null) {
            query.and("type = ?", type);
        }
        if (status != null) {
            query.and("status = ?", status);
        }
        if (bindStaffId != null) {
            query.and("bind_staff_id = ?", bindStaffId);
        }
        
        query.orderBy("created_at DESC");
        
        Page<Customer> page = new Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }
    
    @Override
    public Optional<Customer> findByPhone(String phone) {
        QueryWrapper query = QueryWrapper.create()
                .where("phone = ?", phone)
                .and("deleted = 0");
        Customer customer = mapper.selectOneByQuery(query);
        return Optional.ofNullable(customer);
    }

    @Override
    public List<Customer> findActiveByKeyword(String keyword, int limit) {
        String trimmed = keyword == null ? "" : keyword.trim();
        QueryWrapper query = QueryWrapper.create()
                .where("deleted = 0")
                .and("status = ?", 1);
        if (!trimmed.isEmpty()) {
            String like = "%" + trimmed + "%";
            query.and("(name LIKE ? OR code LIKE ? OR phone LIKE ?)", like, like, like);
        }
        query.orderBy("created_at DESC").limit(Math.max(1, Math.min(limit, 50)));
        return mapper.selectListByQuery(query);
    }
    
    @Override
    public void logicDelete(Long id) {
        Customer customer = mapper.selectOneById(id);
        if (customer != null) {
            customer.setDeleted(1);
            mapper.update(customer);
        }
    }

    @Override
    public long countByBindStaffId(Long staffId) {
        if (staffId == null) return 0;
        QueryWrapper query = QueryWrapper.create()
                .where("bind_staff_id = ?", staffId)
                .and("(deleted IS NULL OR deleted = 0)");
        return mapper.selectCountByQuery(query);
    }

    @Override
    public java.util.List<Customer> findByBindStaffId(Long staffId) {
        if (staffId == null) return java.util.Collections.emptyList();
        QueryWrapper query = QueryWrapper.create()
                .where("bind_staff_id = ?", staffId)
                .and("(deleted IS NULL OR deleted = 0)")
                .and("status = 1");
        return mapper.selectListByQuery(query);
    }

    @Override
    public java.util.List<Customer> findAllWithBindStaff() {
        QueryWrapper query = QueryWrapper.create()
                .where("bind_staff_id IS NOT NULL")
                .and("(deleted IS NULL OR deleted = 0)")
                .and("status = 1");
        return mapper.selectListByQuery(query);
    }
}


