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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    
    @Override
    public Customer save(Customer customer) {
        if(customer==null)throw new IllegalArgumentException("客户资料不能为空");
        if (customer.getId() == null) {
            if(customer.getAuthVersion()!=null&&customer.getAuthVersion()!=1L)throw stale();
            customer.setAuthVersion(1L);
            mapper.insert(customer);
        } else {
            Long expected=customer.getAuthVersion();if(expected==null||expected<=0)throw stale();
            var current=authState(customer.getId(),true).orElseThrow(CustomerRepositoryImpl::stale);
            if(!expected.equals(current.authVersion())||!Integer.valueOf(0).equals(current.deleted()))throw stale();
            if(customer.getDeleted()!=null&&!java.util.Objects.equals(customer.getDeleted(),current.deleted()))
                throw new IllegalArgumentException("删除客户须走原删除入口，不能通过普通资料保存改变");
            Integer status=customer.getStatus()==null?current.status():customer.getStatus();
            if(status==null||status!=1&&status!=2)throw new IllegalArgumentException("客户状态无效");
            boolean changed=!java.util.Objects.equals(status,current.status());
            if(changed&&expected==Long.MAX_VALUE)throw new IllegalStateException("客户授权版本已达上限，请人工处理");
            long next=changed?expected+1:expected;
            customer.setAuthVersion(next);
            try {
                int updated=mapper.updateByQuery(customer,QueryWrapper.create().where("id = ?",customer.getId()).and("auth_version = ?",expected));
                if(updated!=1){
                    // useAffectedRows=true时未修改任何字段可返回0；仅普通无变化保存可核当前版本后接受。
                    var after=updated==0&&!changed?authState(customer.getId(),false).orElse(null):null;
                    if(after==null||!expected.equals(after.authVersion())||!Integer.valueOf(0).equals(after.deleted()))throw stale();
                }
            } catch(RuntimeException error){customer.setAuthVersion(expected);throw error;}
        }
        return customer;
    }

    @Override public Optional<com.agri_system.domain.customer.model.CustomerAuthState> findAuthStateById(Long id){return authState(id,false);}
    private Optional<com.agri_system.domain.customer.model.CustomerAuthState> authState(Long id,boolean lock){
        if(id==null||id<=0)throw new IllegalArgumentException("客户编号无效");
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Optional<com.agri_system.domain.customer.model.CustomerAuthState>>) connection->{
            try(var statement=connection.prepareStatement("SELECT id,status,deleted,auth_version FROM customer WHERE id=?"+(lock?" FOR UPDATE":""))){
                statement.setLong(1,id);try(var row=statement.executeQuery()){
                    if(!row.next())return Optional.empty();long actual=row.getLong("id");int status=row.getInt("status");Integer state=row.wasNull()?null:status;
                    int deleted=row.getInt("deleted");Integer removed=row.wasNull()?null:deleted;long version=row.getLong("auth_version");Long epoch=row.wasNull()?null:version;
                    return Optional.of(new com.agri_system.domain.customer.model.CustomerAuthState(actual,state,removed,epoch));
                }
            }
        });
    }
    private static IllegalStateException stale(){return new IllegalStateException("客户授权状态或版本已改变，请重新读取客户资料");}
    
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

    @Override public List<Long> findIdsByPhoneIncludingDeleted(String phone) {
        if(phone==null||!phone.matches("1[3-9][0-9]{9}"))throw new IllegalArgumentException("核验手机号无效");
        return mapper.selectIdsByPhoneIncludingDeleted(phone);
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
        var current=authState(id,true).orElse(null);if(current==null||Integer.valueOf(1).equals(current.deleted()))return;
        if(!Integer.valueOf(0).equals(current.deleted()))throw stale();
        Long expected=current.authVersion();if(expected==null||expected<=0)throw stale();
        if(expected==Long.MAX_VALUE)throw new IllegalStateException("客户授权版本已达上限，请人工处理");
        int changed=jdbc.update("UPDATE customer SET deleted=1,auth_version=auth_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND auth_version=? AND (deleted=0 OR deleted IS NULL)",id,expected);
        if(changed!=1)throw stale();
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


