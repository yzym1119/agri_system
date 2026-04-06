package com.agri_system.application.system.dict.dto;

import lombok.Data;

/**
 * 字典数据管理VO（包含ID和状态，用于管理端）
 */
@Data
public class DictDataManageVO {
    private Long id;
    private String typeCode;
    private String label;
    private String value;
    private Integer sortOrder;
    private Integer status;
    private Integer isDefault;
}






