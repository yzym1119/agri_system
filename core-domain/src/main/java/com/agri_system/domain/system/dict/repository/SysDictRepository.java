package com.agri_system.domain.system.dict.repository;

import com.agri_system.domain.system.dict.model.SysDictData;
import com.agri_system.domain.system.dict.model.SysDictType;

import java.util.List;
import java.util.Optional;

/**
 * 字典仓储接口
 */
public interface SysDictRepository {

    Optional<SysDictType> findTypeByCode(String code);

    List<SysDictData> listDataByTypeCode(String typeCode);

    SysDictData saveData(SysDictData data);

    Optional<SysDictData> findDataById(Long id);

    void deleteData(Long id);
}






