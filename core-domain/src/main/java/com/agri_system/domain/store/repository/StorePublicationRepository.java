package com.agri_system.domain.store.repository;
import java.util.Optional;
/** 公开设置独立列不映射回Store整对象，避免普通保存覆盖发布状态。 */
public interface StorePublicationRepository {
    record State(Long id,String name,String phone,String address,Integer status,Integer deleted,Integer visible,Long version) {}
    Optional<State> find(Long id,boolean lock);
    boolean setVisible(Long id,Long expectedVersion,int visible);
}
