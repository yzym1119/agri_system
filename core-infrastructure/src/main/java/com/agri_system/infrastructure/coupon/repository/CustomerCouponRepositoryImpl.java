package com.agri_system.infrastructure.coupon.repository;

import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.infrastructure.coupon.mapper.CustomerCouponMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 客户优惠券仓储实现
 * 
 * @author AI Generated
 */
@Repository
@RequiredArgsConstructor
public class CustomerCouponRepositoryImpl implements CustomerCouponRepository {
    
    private final CustomerCouponMapper mapper;
    
    @Override
    public CustomerCoupon save(CustomerCoupon coupon) {
        if (coupon.getId() == null) {
            mapper.insert(coupon);
        } else {
            mapper.update(coupon);
        }
        return coupon;
    }
    
    @Override
    public Optional<CustomerCoupon> findById(Long id) {
        return Optional.ofNullable(mapper.selectOneById(id));
    }
    
    @Override
    public Page<CustomerCoupon> findPage(Integer pageNum, Integer pageSize, Long customerId, Integer status) {
        QueryWrapper query = QueryWrapper.create()
                .where("customer_id = ?", customerId);
        
        if (status != null) {
            query.and("status = ?", status);
        }
        
        query.orderBy("created_at DESC");
        
        Page<CustomerCoupon> page = new Page<>(pageNum, pageSize);
        return mapper.paginate(page, query);
    }
    
    @Override
    public long countByTemplateId(Long templateId) {
        return mapper.countByTemplateId(templateId);
    }
}








