package com.agri_system.domain.store.repository;

import com.agri_system.domain.store.model.Staff;

import java.util.Optional;

/**
 * 员工仓储接口
 * 
 * @author AI Generated
 */
public interface StaffRepository {
    
    /**
     * 根据ID查找员工
     */
    Optional<Staff> findById(Long id);
    
    /**
     * 根据编码或手机号查找员工（用于登录）
     * 
     * @param codeOrPhone 编码或手机号
     * @return 员工
     */
    Optional<Staff> findByCodeOrPhone(String codeOrPhone);

    /**
     * 保存员工
     */
    Staff save(Staff staff);

    /**
     * 更新状态
     */
    void updateStatus(Long staffId, Integer status);

    /**
     * 重置密码
     */
    void resetPassword(Long staffId, String password);
    
    /**
     * 分页查询员工
     */
    com.mybatisflex.core.paginate.Page<Staff> findPage(String code, String name, Long storeId, Integer status, int pageNum, int pageSize);
}




