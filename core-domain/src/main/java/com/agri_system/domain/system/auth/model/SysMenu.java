package com.agri_system.domain.system.auth.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

@Data
@Table("sys_menu")
public class SysMenu {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long parentId;

    private String name;

    private String permission;

    private Integer type;

    private String path;

    private String icon;

    private Integer sortOrder;

    private Integer visible;
}





