package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.customer.dto.*;
import com.agri_system.application.customer.dto.query.*;
import com.agri_system.application.customer.dto.vo.*;
import com.agri_system.application.customer.service.CustomerApplicationService;
import com.agri_system.application.customer.service.SpecialFundGrantService;
import com.agri_system.application.customer.service.WalletRechargeService;
import com.agri_system.application.wallet.service.WalletApplicationService;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.CouponTemplate;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.repository.CouponTemplateRepository;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户管理控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/customer")
@Tag(name = "客户管理", description = "充值、发券、调账等")
@RequiredArgsConstructor
public class CustomerAdminController {
    
    private final CustomerApplicationService customerService;
    private final WalletRechargeService rechargeService;
    private final WalletApplicationService walletService;
    private final SpecialFundGrantService specialFundGrantService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CustomerCouponRepository couponRepository;
    private final CustomerDomainService customerDomainService;
    private final CustomerBenefitLogRepository benefitLogRepository;
    private final com.agri_system.application.marketing.service.CouponService couponService;
    
    /**
     * 分页查询客户列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询客户列表", description = "支持姓名、手机号、类型、状态、归属员工筛选")
    @SaCheckPermission("customer:query")
    public ResponseEntity<Page<CustomerListVO>> list(@Valid CustomerPageQuery query) {
        Page<CustomerListVO> page = customerService.queryCustomerPage(query);
        return ResponseEntity.ok(page);
    }
    
    /**
     * 创建客户
     */
    @PostMapping
    @Operation(summary = "创建客户", description = "创建新客户并初始化三钱包")
    @SaCheckPermission("customer:create")
    public ResponseEntity<CustomerDetailVO> create(@Valid @RequestBody CustomerCreateCommand command) {
        // 设置操作人ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        command.setCreatedBy(staffId);
        
        CustomerDetailVO detail = customerService.createCustomer(command);
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 获取客户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取客户详情", description = "包含客户基本信息和钱包信息")
    @SaCheckPermission("customer:query")
    public ResponseEntity<CustomerDetailVO> getDetail(@PathVariable Long id) {
        CustomerDetailVO detail = customerService.getCustomerDetail(id);
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 查询钱包交易记录
     */
    @GetMapping("/wallet-transactions")
    @Operation(summary = "查询钱包交易记录", description = "分页查询客户的钱包交易记录")
    @SaCheckPermission("customer:query")
    public ResponseEntity<Page<WalletTransactionVO>> getWalletTransactions(@Valid WalletTransactionQuery query) {
        Page<WalletTransactionVO> page = customerService.queryWalletTransactionPage(query);
        return ResponseEntity.ok(page);
    }
    
    /**
     * 查询VIP月度权益发放记录（保留用于兼容）
     */
    @GetMapping("/vip-monthly-records")
    @Operation(summary = "查询VIP月度权益发放记录", description = "分页查询客户的VIP月度权益发放记录（已废弃，请使用 /benefit-logs）")
    @SaCheckPermission("customer:query")
    public ResponseEntity<Page<VipMonthlyRecordVO>> getVipMonthlyRecords(@Valid VipMonthlyRecordQuery query) {
        Page<VipMonthlyRecordVO> page = customerService.queryVipMonthlyRecordPage(query);
        return ResponseEntity.ok(page);
    }
    
    /**
     * 查询客户权益流水
     */
    @GetMapping("/{id}/benefit-logs")
    @Operation(summary = "查询客户权益流水", description = "分页查询客户的权益获取流水（包括每月权益、充值奖励、手动发放）")
    @SaCheckPermission("customer:query")
    public ResponseEntity<Page<CustomerBenefitLogVO>> getBenefitLogs(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        CustomerBenefitLogQuery query = new CustomerBenefitLogQuery();
        query.setCustomerId(id);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        Page<CustomerBenefitLogVO> page = customerService.queryBenefitLogPage(query);
        return ResponseEntity.ok(page);
    }
    
    /**
     * 查询客户优惠券列表
     */
    @GetMapping("/{id}/coupons")
    @Operation(summary = "查询客户优惠券列表", description = "分页查询客户的优惠券（可筛选状态：1-未使用, 2-已使用, 3-已过期, 4-已作废）")
    @SaCheckPermission("customer:query")
    public ResponseEntity<Page<CustomerCouponVO>> getCustomerCoupons(
            @PathVariable Long id,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        CustomerCouponQuery query = new CustomerCouponQuery();
        query.setCustomerId(id);
        query.setStatus(status);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        Page<CustomerCouponVO> page = customerService.queryCustomerCouponPage(query);
        return ResponseEntity.ok(page);
    }
    
    /**
     * 更新客户
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新客户", description = "更新客户信息")
    @SaCheckPermission("customer:update")
    public ResponseEntity<CustomerDetailVO> update(@PathVariable Long id, @Valid @RequestBody CustomerUpdateCommand command) {
        // 设置操作人ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        command.setUpdatedBy(staffId);
        
        CustomerDetailVO detail = customerService.updateCustomer(id, command);
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 删除客户（逻辑删除）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户", description = "逻辑删除客户，需检查余额和欠款")
    @SaCheckPermission("customer:delete")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 充值
     */
    @PostMapping("/recharge")
    @Operation(summary = "充值", description = "客户充值，触发营销规则")
    @SaCheckPermission("customer:recharge")
    public ResponseEntity<RechargeResultVO> recharge(@Valid @RequestBody RechargeCommand command) {
        // 设置操作人ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        command.setCreatedBy(staffId);
        
        RechargeResultVO result = rechargeService.recharge(command);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 手动发券
     */
    @PostMapping("/grant-coupon")
    @Operation(summary = "手动发券", description = "根据模板发放优惠券")
    @SaCheckPermission("customer:coupon:grant")
    public ResponseEntity<Map<String, Object>> grantCoupon(@Valid @RequestBody GrantCouponCommand command) {
        // 设置操作人ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        command.setCreatedBy(staffId);
        
        CouponTemplate template = couponTemplateRepository.findById(command.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("优惠券模板不存在"));
        
        int count = command.getCount() != null ? command.getCount() : 1;
        
        // 校验发放（状态和总量）
        couponService.validateGrant(command.getTemplateId(), command.getCustomerId(), count);
        
        // 发放优惠券
        for (int i = 0; i < count; i++) {
            CustomerCoupon coupon = customerDomainService.createCouponFromTemplate(
                    template, command.getCustomerId());
            couponRepository.save(coupon);
        }
        
        // 记录权益流水
        CustomerBenefitLog benefitLog = new CustomerBenefitLog();
        benefitLog.setCustomerId(command.getCustomerId());
        benefitLog.setSourceType(3); // 3-手动发放
        benefitLog.setBenefitContent(template.getName() + "×" + count);
        benefitLog.setAssociatedId(template.getId());
        benefitLog.setCreatedBy(staffId);
        benefitLog.setCreatedAt(java.time.LocalDateTime.now());
        benefitLogRepository.save(benefitLog);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "发放成功");
        result.put("count", count);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 手动发专用金
     */
    @PostMapping("/grant-special-fund")
    @Operation(summary = "手动发专用金", description = "发放专用金到客户账户")
    @SaCheckPermission("customer:fund:grant")
    public ResponseEntity<Map<String, Object>> grantSpecialFund(@Valid @RequestBody GrantSpecialFundCommand command) {
        // 设置操作人ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        command.setCreatedBy(staffId);
        
        // 余额与权益台账由应用服务在同一事务写入，避免台账失败导致钱账不一致。
        BigDecimal balance = specialFundGrantService.grant(
                command.getCustomerId(),
                command.getAmount(),
                command.getCreatedBy(),
                command.getRemark()
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "发放成功");
        result.put("balance", balance);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 手动调账
     */
    @PostMapping("/grant-balance")
    @Operation(summary = "手动调账", description = "调整客户本金余额")
    @SaCheckPermission("customer:balance:grant")
    public ResponseEntity<Map<String, Object>> grantBalance(@Valid @RequestBody GrantBalanceCommand command) {
        BigDecimal balance;
        if (command.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            // 增加
            balance = walletService.addBalance(
                    command.getCustomerId(),
                    command.getAmount(),
                    "MANUAL_ADJUST",
                    null,
                    command.getCreatedBy(),
                    command.getRemark()
            );
        } else {
            // 减少
            balance = walletService.deductBalance(
                    command.getCustomerId(),
                    command.getAmount().abs(),
                    "MANUAL_ADJUST",
                    null,
                    command.getCreatedBy(),
                    command.getRemark()
            );
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "调账成功");
        result.put("balance", balance);
        return ResponseEntity.ok(result);
    }
}






