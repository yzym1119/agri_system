package com.agri_system.domain.store.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工实体
 *
 * @author AI Generated
 */
@Data
@Table("staff")
public class Staff {

    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 员工编码 */
    private String code;

    /** 员工姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 所属门店ID */
    @Column(tenantId = true)
    private Long storeId;

    /** 密码（BCrypt加密） */
    private String password;

    /** 角色：ADMIN-管理员，MANAGER-店长，STAFF-员工，DRIVER-司机 */
    private String role;

    /** 岗位（如：店长、收银员、仓管、农技顾问） */
    private String position;

    /** 班次类型（如：早班、晚班、全天） */
    private String shiftType;

    /** 基本底薪（元） */
    private BigDecimal salaryBase;

    /** 状态：1-在职，2-离职 */
    private Integer status;

    /** 逻辑删除：1-已删，0-未删 */
    private Integer deleted;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 合同到期日 */
    private LocalDate contractEndDate;

    /** 紧急联系人 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;
    
    /**
     * 检查员工是否在职
     */
    public boolean isActive() {
        return status != null && status == 1;
    }
    
    /**
     * 检查员工是否已删除
     */
    public boolean hasBeenDeleted() {
        return deleted != null && deleted == 1;
    }
    
    /**
     * 检查员工是否是店长或管理员
     */
    public boolean isManagerOrAdmin() {
        return "MANAGER".equals(role) || "ADMIN".equals(role);
    }
}

