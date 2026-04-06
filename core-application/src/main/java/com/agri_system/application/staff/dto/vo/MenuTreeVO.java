package com.agri_system.application.staff.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "菜单树节点")
public class MenuTreeVO {

    private Long id;
    private Long parentId;
    private String name;
    private String permission;
    private Integer type;
    private Integer sortOrder;
    private List<MenuTreeVO> children = new ArrayList<>();
}





