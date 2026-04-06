package com.agri_system.domain.common.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编码序号实体
 * 
 * @author AI Generated
 */
@Data
@Table("sys_code_sequence")
public class CodeSequence {
    
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 编码类型：STORE, STAFF, PRODUCT, CUSTOMER, SUPPLIER, ROLE */
    private String codeType;
    
    /** 最后一个编码 */
    private String lastCode;
    
    /** 当前序号 */
    private Long sequence;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
}






