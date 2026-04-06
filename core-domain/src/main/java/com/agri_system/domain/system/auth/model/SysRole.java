package com.agri_system.domain.system.auth.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("sys_role")
public class SysRole {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String name;

    private String code;

    private Integer status;

    private String remark;

    private LocalDateTime createdAt;
}





