package com.agri_system.infrastructure.customer.repository;

import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.infrastructure.customer.mapper.CouponTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 优惠券模板仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {
    
    private final CouponTemplateMapper mapper;
    
    @Override
    public CouponTemplate save(CouponTemplate template) {
        if (template.getId() == null) {
            mapper.insert(template);
        } else {
            mapper.update(template);
        }
        return template;
    }
    
    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }
    
    @Override
    public List<CouponTemplate> findAllEnabled() {
        return mapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where("status = ?", 1)
        );
    }
    
    @Override
    public com.mybatisflex.core.paginate.Page<CouponTemplate> findPage(Integer pageNum, Integer pageSize, String name, Integer status) {
        com.mybatisflex.core.query.QueryWrapper query = com.mybatisflex.core.query.QueryWrapper.create();
        if (name != null && !name.isEmpty()) {
            query.where("name LIKE ?", "%" + name + "%");
        }
        if (status != null) {
            query.and("status = ?", status);
        }
        query.orderBy("created_at DESC");
        com.mybatisflex.core.paginate.Page<CouponTemplate> page = new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }
    
    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }
}







