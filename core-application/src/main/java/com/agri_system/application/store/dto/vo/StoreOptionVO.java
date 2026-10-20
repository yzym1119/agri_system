package com.agri_system.application.store.dto.vo;

/** 业务选项仅包含门店标识，不暴露管理档案或联系方式。 */
public record StoreOptionVO(Long id,String name) {}
