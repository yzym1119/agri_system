package com.agri_system.infrastructure.store.repository;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.infrastructure.store.mapper.StaffMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 员工仓储实现（用于店长校验）
 * 注意：完整的 Staff 模块将在后续实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class StaffRepositoryImpl implements StaffRepository {
    
    private final StaffMapper staffMapper;
    
    @Override
    public Optional<Staff> findById(Long id) {
        Staff staff = staffMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("id = ?", id)
                        .and("deleted = 0")
        );
        return Optional.ofNullable(staff);
    }
    
    @Override
    public Optional<Staff> findIdentityById(Long id) {
        return Optional.ofNullable(staffMapper.selectIdentityById(id));
    }

    @Override
    public Optional<Staff> findByIdForUpdate(Long id) {
        return Optional.ofNullable(staffMapper.selectOneByQuery(QueryWrapper.create()
                .where("id = ?", id).and("deleted = 0").forUpdate()));
    }

    @Override
    public void transferStore(Long staffId, Long storeId) {
        if (staffMapper.transferStore(staffId, storeId) != 1) throw new IllegalStateException("员工调店失败");
    }

    @Override
    public Optional<Staff> findByCodeOrPhone(String codeOrPhone) {
        Staff staff = staffMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("(code = ? OR phone = ?)", codeOrPhone, codeOrPhone)
                        .and("deleted = 0")
        );
        return Optional.ofNullable(staff);
    }

    @Override
    public Staff save(Staff staff) {
        if (staff.getId() == null) {
            staffMapper.insert(staff);
        } else {
            staffMapper.update(staff);
        }
        return staff;
    }

    @Override
    public void updateStatus(Long staffId, Integer status) {
        Staff staff = staffMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("id = ?", staffId)
                        .and("deleted = 0")
        );
        if (staff != null) {
            staff.setStatus(status);
            staffMapper.update(staff);
        }
    }

    @Override
    public void resetPassword(Long staffId, String password) {
        Staff staff = staffMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where("id = ?", staffId)
                        .and("deleted = 0")
        );
        if (staff != null) {
            staff.setPassword(password);
            staffMapper.update(staff);
        }
    }
    
    @Override
    public Page<Staff> findPage(String code, String name, Long storeId, Integer status, int pageNum, int pageSize) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("deleted = 0");
        
        if (code != null && !code.isEmpty()) {
            queryWrapper.and("code LIKE ?", "%" + code + "%");
        }
        if (name != null && !name.isEmpty()) {
            queryWrapper.and("name LIKE ?", "%" + name + "%");
        }
        if (storeId != null) {
            queryWrapper.and("store_id = ?", storeId);
        }
        if (status != null) {
            queryWrapper.and("status = ?", status);
        }
        
        queryWrapper.orderBy("created_at DESC");
        
        return staffMapper.paginate(pageNum, pageSize, queryWrapper);
    }

    @Override
    public long countByStoreId(Long storeId) {
        if (storeId == null) {
            return 0;
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("store_id = ?", storeId)
                .and("deleted = 0");
        return staffMapper.selectCountByQuery(queryWrapper);
    }
}



