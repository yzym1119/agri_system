package com.agri_system.domain.system.dict.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据实体
 */
@Data
@Table("sys_dict_data")
public class SysDictData {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 字典类型编码 */
    private String typeCode;

    /** 展示名称 */
    private String label;

    /** 存储值 */
    private String value;

    /** 排序 */
    private Integer sortOrder;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 是否默认 */
    private Integer isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isEnabled() {
        return status == null || status == 1;
    }
}






