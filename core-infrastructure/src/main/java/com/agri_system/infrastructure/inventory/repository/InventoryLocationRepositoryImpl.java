package com.agri_system.infrastructure.inventory.repository;

import com.agri_system.domain.inventory.model.InventoryLocation;
import com.agri_system.domain.inventory.repository.InventoryLocationRepository;
import com.agri_system.infrastructure.inventory.mapper.InventoryLocationMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InventoryLocationRepositoryImpl implements InventoryLocationRepository {
    private final InventoryLocationMapper mapper;

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
                .and("location_type = ?", locationType).and("status = 1");
        return Optional.ofNullable(mapper.selectOneByQuery(query));
    }
}
