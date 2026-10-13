package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryLocationMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryLocationRepositoryImpl implements InventoryLocationRepository {
    private final InventoryLocationMapper mapper;

    @Override
    public InventoryLocation defaultBackroom(Long storeId) {
        InventoryLocation location = mapper.defaultBackroom(storeId);
        if (location == null) {
            mapper.ensureBackroom(storeId);
            location = mapper.defaultBackroomForUpdate(storeId);
        }
        if (location == null || !Integer.valueOf(1).equals(location.getStatus())) throw new IllegalStateException("默认后仓已停用，请先恢复货位");
        return location;
    }

    @Override
    public void save(InventoryLocation location) {
        if (location.getId() == null) mapper.insert(location); else mapper.update(location);
    }

    @Override
    public Optional<InventoryLocation> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }

    @Override
    public Optional<InventoryLocation> findEnabledByStoreAndType(Long storeId, String locationType) {
        QueryWrapper query = QueryWrapper.create().where("store_id = ?", storeId)
                .and("location_type = ?", locationType).and("status = 1").orderBy("id ASC").limit(1);
        return Optional.ofNullable(mapper.selectOneByQuery(query));
    }

    @Override
    public List<InventoryLocation> findByStoreId(Long storeId) {
        QueryWrapper query = QueryWrapper.create().where("store_id = ?", storeId).orderBy("id ASC");
        return mapper.selectListByQuery(query);
    }

    @Override
    public Optional<InventoryLocation> findByStoreIdAndCode(Long storeId, String code) {
        QueryWrapper query = QueryWrapper.create().where("store_id = ?", storeId)
                .and("code = ?", code).and("status = 1");
        return Optional.ofNullable(mapper.selectOneByQuery(query));
    }
}
