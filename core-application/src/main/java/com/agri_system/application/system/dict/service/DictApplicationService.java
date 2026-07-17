package com.agri_system.application.system.dict.service;

import com.agri_system.application.system.dict.dto.DictDataCreateCommand;
import com.agri_system.application.system.dict.dto.DictDataManageVO;
import com.agri_system.application.system.dict.dto.DictDataUpdateCommand;
import com.agri_system.application.system.dict.dto.DictDataVO;
import com.agri_system.domain.system.dict.model.SysDictData;
import com.agri_system.domain.system.dict.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictApplicationService {

    private final SysDictRepository dictRepository;

    @Cacheable(value = "sys_dict", key = "#typeCode")
    public List<DictDataVO> listByCode(String typeCode) {
        ensureTypeExists(typeCode);
        return dictRepository.listDataByTypeCode(typeCode)
                .stream()
                .filter(SysDictData::isEnabled)
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 管理端：获取字典数据列表（包含所有状态和ID）
     */
    public List<DictDataManageVO> listDataForManage(String typeCode) {
        ensureTypeExists(typeCode);
        return dictRepository.listDataByTypeCode(typeCode)
                .stream()
                .map(this::toManageVO)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "sys_dict", key = "#command.typeCode")
    public Long createData(DictDataCreateCommand command) {
        ensureTypeExists(command.getTypeCode());
        normalizeDefaultFlag(command.getTypeCode(), null, command.getIsDefault());

        SysDictData data = new SysDictData();
        data.setTypeCode(command.getTypeCode());
        data.setLabel(command.getLabel());
        data.setValue(command.getValue());
        data.setSortOrder(command.getSortOrder());
        data.setStatus(command.getStatus());
        data.setIsDefault(command.getIsDefault());

        dictRepository.saveData(data);
        return data.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "sys_dict", key = "#command.typeCode")
    public void updateData(DictDataUpdateCommand command) {
        SysDictData data = dictRepository.findDataById(command.getId())
                .orElseThrow(() -> new IllegalArgumentException("字典数据不存在"));
        if (!data.getTypeCode().equals(command.getTypeCode())) {
            throw new IllegalArgumentException("字典类型不匹配");
        }
        normalizeDefaultFlag(command.getTypeCode(), data.getId(), command.getIsDefault());
        data.setLabel(command.getLabel());
        data.setValue(command.getValue());
        data.setSortOrder(command.getSortOrder());
        data.setStatus(command.getStatus());
        data.setIsDefault(command.getIsDefault());

        dictRepository.saveData(data);
    }

    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "sys_dict", key = "#typeCode")
    public void deleteData(Long id, String typeCode) {
        SysDictData data = dictRepository.findDataById(id)
                .orElseThrow(() -> new IllegalArgumentException("字典数据不存在"));
        if (!data.getTypeCode().equals(typeCode)) {
            throw new IllegalArgumentException("字典类型不匹配");
        }
        if (data.isEnabled()) {
            throw new IllegalArgumentException("启用中的字典项不能直接删除，请先停用后再删除");
        }
        if (data.getIsDefault() != null && data.getIsDefault() == 1) {
            throw new IllegalArgumentException("默认字典项不能直接删除，请先取消默认后再删除");
        }
        dictRepository.deleteData(id);
    }

    private void normalizeDefaultFlag(String typeCode, Long currentId, Integer isDefault) {
        if (isDefault == null || isDefault != 1) {
            return;
        }
        List<SysDictData> exists = dictRepository.listDataByTypeCode(typeCode);
        for (SysDictData item : exists) {
            if (item.getIsDefault() != null
                    && item.getIsDefault() == 1
                    && (currentId == null || !item.getId().equals(currentId))) {
                item.setIsDefault(0);
                dictRepository.saveData(item);
            }
        }
    }

    private void ensureTypeExists(String typeCode) {
        dictRepository.findTypeByCode(typeCode)
                .filter(t -> t.getStatus() == null || t.getStatus() == 1)
                .orElseThrow(() -> new IllegalArgumentException("字典类型不存在或已停用"));
    }

    private DictDataVO toVO(SysDictData data) {
        DictDataVO vo = new DictDataVO();
        vo.setLabel(data.getLabel());
        vo.setValue(data.getValue());
        vo.setSortOrder(data.getSortOrder());
        vo.setIsDefault(data.getIsDefault());
        return vo;
    }

    private DictDataManageVO toManageVO(SysDictData data) {
        DictDataManageVO vo = new DictDataManageVO();
        vo.setId(data.getId());
        vo.setTypeCode(data.getTypeCode());
        vo.setLabel(data.getLabel());
        vo.setValue(data.getValue());
        vo.setSortOrder(data.getSortOrder());
        vo.setStatus(data.getStatus());
        vo.setIsDefault(data.getIsDefault());
        return vo;
    }
}





