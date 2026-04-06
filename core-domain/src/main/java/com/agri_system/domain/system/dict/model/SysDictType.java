package com.agri_system.domain.system.dict.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型实体
 */
@Data
@Table("sys_dict_type")
public class SysDictType {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 字典编码 */
    private String code;

    /** 字典名称 */
    private String name;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isActive() {
        return status == null || status == 1;
    }
}






