package com.agri_system.application.purchase.dto.query;

import lombok.Data;

/**
 * 供应商查询参数
 * 
 * @author AI Generated
 */
@Data
public class SupplierQuery {
    
    /** 供应商编码 */
    private String code;
    
    /** 供应商名称 */
    private String name;
    
    /** 状态：1-正常，2-停用 */
    private Integer status;
    
    /** 页码（从1开始） */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 10;
}

