package com.agri_system.infrastructure.after_sales.repository;

import com.agri_system.domain.after_sales.model.AfterSalesItem;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesItemMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 售后明细仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AfterSalesItemRepositoryImpl implements AfterSalesItemRepository {

    private final AfterSalesItemMapper mapper;

    @Override
    public void saveBatch(List<AfterSalesItem> items) {
        for (AfterSalesItem item : items) {
            if (item.getId() == null) {
                mapper.insert(item);
            } else {
                mapper.update(item);
            }
        }
    }

    @Override
    public List<AfterSalesItem> findByAfterSalesId(Long afterSalesId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where("after_sales_id = ?", afterSalesId)
                .orderBy("id ASC");
        return mapper.selectListByQuery(wrapper);
    }
}








