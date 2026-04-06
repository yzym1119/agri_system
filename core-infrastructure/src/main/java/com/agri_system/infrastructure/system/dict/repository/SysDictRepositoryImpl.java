package com.agri_system.infrastructure.system.dict.repository;

import com.agri_system.domain.system.dict.model.SysDictData;
import com.agri_system.domain.system.dict.model.SysDictType;
import com.agri_system.domain.system.dict.repository.SysDictRepository;
import com.agri_system.infrastructure.system.dict.mapper.SysDictDataMapper;
import com.agri_system.infrastructure.system.dict.mapper.SysDictTypeMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SysDictRepositoryImpl implements SysDictRepository {

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    @Override
    public Optional<SysDictType> findTypeByCode(String code) {
        SysDictType type = typeMapper.selectOneByQuery(
                QueryWrapper.create().where("code = ?", code)
        );
        return Optional.ofNullable(type);
    }

    @Override
    public List<SysDictData> listDataByTypeCode(String typeCode) {
        return dataMapper.selectListByQuery(
                QueryWrapper.create()
                        .where("type_code = ?", typeCode)
                        .orderBy("sort_order", true)
                        .orderBy("id", true)
        );
    }

    @Override
    public SysDictData saveData(SysDictData data) {
        if (data.getId() == null) {
            dataMapper.insert(data);
        } else {
            dataMapper.update(data);
        }
        return data;
    }

    @Override
    public Optional<SysDictData> findDataById(Long id) {
        return Optional.ofNullable(dataMapper.selectOneById(id));
    }

    @Override
    public void deleteData(Long id) {
        dataMapper.deleteById(id);
    }
}






