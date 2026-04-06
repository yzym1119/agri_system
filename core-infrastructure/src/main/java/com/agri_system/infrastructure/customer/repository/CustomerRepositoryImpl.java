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
        return Optional.ofNullable(mapper.selectOneById(id));
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
                .where("phone = ?", phone);
        Customer customer = mapper.selectOneByQuery(query);
        return Optional.ofNullable(customer);
    }
    
    @Override
    public void logicDelete(Long id) {
        Customer customer = mapper.selectOneById(id);
        if (customer != null) {
            customer.setDeleted(1);
            mapper.update(customer);
        }
    }
}



