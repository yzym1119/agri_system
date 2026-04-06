package com.agri_system.application.marketing.service;

import com.agri_system.application.marketing.dto.vo.CouponTemplateVO;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.domain.product.repository.ProductCategoryRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 优惠券模板应用服务
 *
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class CouponTemplateApplicationService {
    
    private final CouponTemplateRepository couponTemplateRepository;
    private final ProductCategoryRepository categoryRepository;
    private final CouponService couponService;
    
    /**
     * 分页查询优惠券模板
     */
    @Transactional(readOnly = true)
    public Page<CouponTemplateVO> queryPage(Integer pageNum, Integer pageSize, String name, Integer status) {
        Page<CouponTemplate> page = couponTemplateRepository.findPage(pageNum, pageSize, name, status);
        
        // 获取所有分类ID
        List<Long> categoryIds = page.getRecords().stream()
                .map(CouponTemplate::getLimitCategoryId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询分类名称
        Map<Long, String> categoryNameMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryNameMap = categoryIds.stream()
                    .map(categoryRepository::findById)
                    .filter(opt -> opt.isPresent())
                    .collect(Collectors.toMap(
                            opt -> opt.get().getId(),
                            opt -> opt.get().getName(),
                            (v1, v2) -> v1
                    ));
        }
        
        // 转换为VO
        final Map<Long, String> finalCategoryNameMap = categoryNameMap;
        List<CouponTemplateVO> voList = page.getRecords().stream()
                .map(template -> {
                    CouponTemplateVO vo = new CouponTemplateVO();
                    vo.setId(template.getId());
                    vo.setName(template.getName());
                    vo.setType(template.getType());
                    vo.setAmount(template.getAmount());
                    vo.setMinOrderAmount(template.getMinOrderAmount());
                    vo.setLimitCategoryId(template.getLimitCategoryId());
                    vo.setLimitCategoryName(template.getLimitCategoryId() != null 
                            ? finalCategoryNameMap.getOrDefault(template.getLimitCategoryId(), null) 
                            : null);
                    vo.setLimitProductId(template.getLimitProductId());
                    vo.setValidityType(template.getValidityType());
                    vo.setValidStartTime(template.getValidStartTime());
                    vo.setValidEndTime(template.getValidEndTime());
                    vo.setValidDays(template.getValidDays());
                    vo.setTotalCount(template.getTotalCount());
                    vo.setStatus(template.getStatus());
                    vo.setRemark(template.getRemark());
                    vo.setCreatedAt(template.getCreatedAt());
                    vo.setUpdatedAt(template.getUpdatedAt());
                    return vo;
                })
                .collect(Collectors.toList());
        
        Page<CouponTemplateVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    /**
     * 获取优惠券模板详情
     */
    @Transactional(readOnly = true)
    public CouponTemplate getDetail(Long id) {
        return couponTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
    }
    
    /**
     * 创建优惠券模板
     */
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplate create(CouponTemplate template) {
        if (template.getStatus() == null) {
            template.setStatus(1); // 默认启用
        }
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return couponTemplateRepository.save(template);
    }
    
    /**
     * 更新优惠券模板
     */
    @Transactional(rollbackFor = Exception.class)
    public CouponTemplate update(Long id, CouponTemplate template) {
        CouponTemplate existing = couponTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        
        // 如果修改了发行总量，需要校验
        if (template.getTotalCount() != null && !template.getTotalCount().equals(existing.getTotalCount())) {
            couponService.validateUpdateTotalCount(id, template.getTotalCount());
        }
        
        template.setId(id);
        template.setUpdatedAt(LocalDateTime.now());
        return couponTemplateRepository.save(template);
    }
    
    /**
     * 删除优惠券模板
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        couponTemplateRepository.deleteById(id);
    }
    
    /**
     * 切换优惠券模板状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        CouponTemplate template = couponTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        template.setStatus(status);
        template.setUpdatedAt(LocalDateTime.now());
        couponTemplateRepository.save(template);
    }
}

