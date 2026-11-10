package com.agri_system.domain.customer.model;

/** 客户安全状态的独立白名单投影，不继承外层业务DTO的Flex映射上下文。 */
public record CustomerAuthState(Long id,Integer status,Integer deleted,Long authVersion) {
    public boolean active(){return id!=null&&id>0&&Integer.valueOf(1).equals(status)&&Integer.valueOf(0).equals(deleted)&&authVersion!=null&&authVersion>0;}
}
