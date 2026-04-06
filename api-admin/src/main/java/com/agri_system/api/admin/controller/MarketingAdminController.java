package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.marketing.dto.vo.CouponTemplateVO;
import com.agri_system.application.marketing.service.CouponTemplateApplicationService;
import com.agri_system.application.marketing.service.MarketingApplicationService;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.model.MarketingRechargeRule;
import com.agri_system.domain.customer.model.MarketingVipRights;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销管理控制器
 *
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/marketing")
@Tag(name = "营销管理", description = "优惠券模板、充值奖励规则、VIP月度权益等")
@RequiredArgsConstructor
public class MarketingAdminController {
    
    private final CouponTemplateApplicationService couponTemplateService;
    private final MarketingApplicationService marketingService;
    
    // ==================== 优惠券模板管理 ====================
    
    @GetMapping("/coupon-template")
    @Operation(summary = "分页查询优惠券模板列表")
    @SaCheckPermission("marketing:coupon:query")
    public ResponseEntity<Page<CouponTemplateVO>> listCouponTemplates(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 10 : pageSize;
        Page<CouponTemplateVO> page = couponTemplateService.queryPage(pn, ps, name, status);
        return ResponseEntity.ok(page);
    }
    
    @GetMapping("/coupon-template/{id}")
    @Operation(summary = "获取优惠券模板详情")
    @SaCheckPermission("marketing:coupon:query")
    public ResponseEntity<CouponTemplate> getCouponTemplateDetail(@PathVariable Long id) {
        CouponTemplate template = couponTemplateService.getDetail(id);
        return ResponseEntity.ok(template);
    }
    
    @PostMapping("/coupon-template")
    @Operation(summary = "创建优惠券模板")
    @SaCheckPermission("marketing:coupon:create")
    public ResponseEntity<Map<String, Object>> createCouponTemplate(@Valid @RequestBody CouponTemplate template) {
        CouponTemplate saved = couponTemplateService.create(template);
        Map<String, Object> result = new HashMap<>();
        result.put("id", saved.getId());
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/coupon-template/{id}")
    @Operation(summary = "更新优惠券模板")
    @SaCheckPermission("marketing:coupon:update")
    public ResponseEntity<Void> updateCouponTemplate(@PathVariable Long id, @Valid @RequestBody CouponTemplate template) {
        couponTemplateService.update(id, template);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/coupon-template/{id}")
    @Operation(summary = "删除优惠券模板")
    @SaCheckPermission("marketing:coupon:delete")
    public ResponseEntity<Void> deleteCouponTemplate(@PathVariable Long id) {
        couponTemplateService.delete(id);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/coupon-template/{id}/status")
    @Operation(summary = "切换优惠券模板状态")
    @SaCheckPermission("marketing:coupon:update")
    public ResponseEntity<Void> toggleCouponTemplateStatus(@PathVariable Long id, @RequestParam Integer status) {
        couponTemplateService.toggleStatus(id, status);
        return ResponseEntity.ok().build();
    }
    
    // ==================== 充值奖励规则 ====================
    
    @GetMapping("/recharge-rule")
    @Operation(summary = "查询充值奖励规则列表")
    @SaCheckPermission("marketing:rule:query")
    public ResponseEntity<List<MarketingRechargeRule>> listRechargeRules() {
        List<MarketingRechargeRule> rules = marketingService.listRechargeRules();
        return ResponseEntity.ok(rules);
    }
    
    @PostMapping("/recharge-rule")
    @Operation(summary = "创建充值奖励规则")
    @SaCheckPermission("marketing:rule:create")
    public ResponseEntity<Map<String, Object>> createRechargeRule(@Valid @RequestBody MarketingRechargeRule rule) {
        MarketingRechargeRule saved = marketingService.saveRechargeRule(rule);
        Map<String, Object> result = new HashMap<>();
        result.put("id", saved.getId());
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/recharge-rule/{id}")
    @Operation(summary = "更新充值奖励规则")
    @SaCheckPermission("marketing:rule:update")
    public ResponseEntity<Void> updateRechargeRule(@PathVariable Long id, @Valid @RequestBody MarketingRechargeRule rule) {
        rule.setId(id);
        marketingService.saveRechargeRule(rule);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/recharge-rule/{id}")
    @Operation(summary = "删除充值奖励规则")
    @SaCheckPermission("marketing:rule:delete")
    public ResponseEntity<Void> deleteRechargeRule(@PathVariable Long id) {
        marketingService.deleteRechargeRule(id);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/recharge-rule/{id}/status")
    @Operation(summary = "切换充值奖励规则状态")
    @SaCheckPermission("marketing:rule:update")
    public ResponseEntity<Void> toggleRechargeRuleStatus(@PathVariable Long id, @RequestParam Integer status) {
        marketingService.toggleRechargeRuleStatus(id, status);
        return ResponseEntity.ok().build();
    }
    
    // ==================== VIP月度权益 ====================
    
    @GetMapping("/vip-rights")
    @Operation(summary = "查询VIP月度权益列表")
    @SaCheckPermission("marketing:rule:query")
    public ResponseEntity<List<MarketingVipRights>> listVipRights() {
        List<MarketingVipRights> rights = marketingService.listVipRights();
        return ResponseEntity.ok(rights);
    }
    
    @GetMapping("/vip-rights/level/{vipLevel}")
    @Operation(summary = "根据VIP等级查询权益")
    @SaCheckPermission("marketing:rule:query")
    public ResponseEntity<List<MarketingVipRights>> getVipRightsByLevel(@PathVariable Integer vipLevel) {
        List<MarketingVipRights> rights = marketingService.getVipRightsByLevel(vipLevel);
        return ResponseEntity.ok(rights);
    }
    
    @PostMapping("/vip-rights")
    @Operation(summary = "保存VIP月度权益（创建或更新，单条）")
    @SaCheckPermission("marketing:rule:create")
    public ResponseEntity<Map<String, Object>> saveVipRights(@Valid @RequestBody MarketingVipRights rights) {
        MarketingVipRights saved = marketingService.saveVipRights(rights);
        Map<String, Object> result = new HashMap<>();
        result.put("id", saved.getId());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/vip-rights/batch")
    @Operation(summary = "批量保存VIP月度权益（支持一个等级多条权益）")
    @SaCheckPermission("marketing:rule:create")
    public ResponseEntity<Void> saveVipRightsBatch(
            @RequestParam Integer vipLevel,
            @Valid @RequestBody List<MarketingVipRights> rightsList) {
        marketingService.saveVipRightsBatch(vipLevel, rightsList);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/vip-rights/{id}")
    @Operation(summary = "删除VIP月度权益")
    @SaCheckPermission("marketing:rule:delete")
    public ResponseEntity<Void> deleteVipRights(@PathVariable Long id) {
        marketingService.deleteVipRights(id);
        return ResponseEntity.ok().build();
    }
}

