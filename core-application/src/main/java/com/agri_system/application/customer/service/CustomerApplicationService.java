package com.agri_system.application.customer.service;

import com.agri_system.application.customer.dto.CustomerCreateCommand;
import com.agri_system.application.customer.dto.CustomerUpdateCommand;
import com.agri_system.application.customer.dto.query.*;
import com.agri_system.application.customer.dto.vo.*;
import com.agri_system.domain.coupon.model.CustomerCoupon;
import com.agri_system.domain.coupon.repository.CustomerCouponRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.model.CustomerBenefitLog;
import com.agri_system.domain.customer.model.VipMonthlyRecord;
import com.agri_system.domain.customer.repository.CustomerBenefitLogRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.customer.repository.VipMonthlyRecordRepository;
import com.agri_system.domain.customer.service.CustomerDomainService;
import com.agri_system.domain.product.repository.ProductCategoryRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.model.WalletTransaction;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.domain.wallet.repository.WalletTransactionRepository;
import com.mybatisflex.core.paginate.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.credit.dto.CreditDebtVO;

/**
 * 客户应用服务
 *
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class CustomerApplicationService {
    
    private final CustomerRepository customerRepository;
    private final CustomerRegistrationService registrationService;
    private final StaffRepository staffRepository;
    private final CustomerWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final VipMonthlyRecordRepository monthlyRecordRepository;
    private final CustomerBenefitLogRepository benefitLogRepository;
    private final CustomerCouponRepository couponRepository;
    private final ProductCategoryRepository categoryRepository;
    private final CustomerDomainService customerDomainService;
    private final CreditApplicationService creditApplicationService;
    
    /**
     * 分页查询客户列表
     */
    @Transactional(readOnly = true)
    public Page<CustomerListVO> queryCustomerPage(CustomerPageQuery query) {
        Page<Customer> page = customerRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getName(),
                query.getPhone(),
                query.getType(),
                query.getStatus(),
                query.getBindStaffId()
        );
        
        // 获取所有员工ID
        List<Long> staffIds = page.getRecords().stream()
                .map(Customer::getBindStaffId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        // 查询员工信息
        Map<Long, String> staffNameMap = staffIds.stream()
                .map(staffRepository::findById)
                .filter(opt -> opt.isPresent())
                .collect(Collectors.toMap(
                        opt -> opt.get().getId(),
                        opt -> opt.get().getName(),
                        (v1, v2) -> v1
                ));
        
        // 获取所有客户ID
        List<Long> customerIds = page.getRecords().stream()
                .map(Customer::getId)
                .collect(Collectors.toList());
        
        // 查询钱包信息
        Map<Long, CustomerWallet> balanceWalletMap = customerIds.stream()
                .map(id -> walletRepository.findByCustomerIdAndWalletType(id, 1))
                .filter(opt -> opt.isPresent())
                .collect(Collectors.toMap(
                        opt -> opt.get().getCustomerId(),
                        opt -> opt.get(),
                        (v1, v2) -> v1
                ));
        
        Map<Long, CustomerWallet> specialFundWalletMap = customerIds.stream()
                .map(id -> walletRepository.findByCustomerIdAndWalletType(id, 2))
                .filter(opt -> opt.isPresent())
                .collect(Collectors.toMap(
                        opt -> opt.get().getCustomerId(),
                        opt -> opt.get(),
                        (v1, v2) -> v1
                ));
        
        Map<Long, CustomerWallet> pointsWalletMap = customerIds.stream()
                .map(id -> walletRepository.findByCustomerIdAndWalletType(id, 3))
                .filter(opt -> opt.isPresent())
                .collect(Collectors.toMap(
                        opt -> opt.get().getCustomerId(),
                        opt -> opt.get(),
                        (v1, v2) -> v1
                ));
        
        // 转换为VO
        List<CustomerListVO> voList = page.getRecords().stream()
                .map(customer -> {
                    CustomerListVO vo = new CustomerListVO();
                    vo.setId(customer.getId());
                    vo.setCode(customer.getCode());
                    vo.setName(customer.getName());
                    vo.setPhone(customer.getPhone());
                    vo.setType(customer.getType());
                    vo.setVipLevel(customer.getVipLevel());
                    vo.setVipEndDate(customer.getVipEndDate());
                    vo.setStatus(customer.getStatus());
                    vo.setCreatedAt(customer.getCreatedAt());
                    vo.setBindStaffId(customer.getBindStaffId());
                    vo.setBindStaffName(staffNameMap.get(customer.getBindStaffId()));
                    
                    // 钱包信息
                    CustomerWallet balanceWallet = balanceWalletMap.get(customer.getId());
                    if (balanceWallet != null) {
                        vo.setBalance(balanceWallet.getBalance());
                    }
                    
                    CustomerWallet specialFundWallet = specialFundWalletMap.get(customer.getId());
                    if (specialFundWallet != null) {
                        vo.setSpecialFundBalance(specialFundWallet.getBalance());
                        vo.setFrozenSpecialFund(specialFundWallet.getFrozenBalance());
                    }
                    
                    CustomerWallet pointsWallet = pointsWalletMap.get(customer.getId());
                    if (pointsWallet != null) {
                        vo.setPoints(pointsWallet.getBalance());
                    }
                    
                    return vo;
                })
                .collect(Collectors.toList());
        
        Page<CustomerListVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    /**
     * 获取客户详情
     */
    @Transactional(readOnly = true)
    public CustomerDetailVO getCustomerDetail(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        
        CustomerDetailVO vo = new CustomerDetailVO();
        vo.setId(customer.getId());
        vo.setCode(customer.getCode());
        vo.setName(customer.getName());
        vo.setPhone(customer.getPhone());
        vo.setType(customer.getType());
        vo.setVipLevel(customer.getVipLevel());
        vo.setVipStartDate(customer.getVipStartDate());
        vo.setVipEndDate(customer.getVipEndDate());
        vo.setAddress(customer.getAddress());
        vo.setIdCard(customer.getIdCard());
        vo.setCreditLimit(customer.getCreditLimit());
        vo.setStatus(customer.getStatus());
        vo.setRemark(customer.getRemark());
        vo.setCreatedAt(customer.getCreatedAt());
        vo.setUpdatedAt(customer.getUpdatedAt());
        vo.setBindStaffId(customer.getBindStaffId());
        
        // 查询员工信息
        if (customer.getBindStaffId() != null) {
            staffRepository.findById(customer.getBindStaffId())
                    .ifPresent(staff -> vo.setBindStaffName(staff.getName()));
        }
        
        // 查询钱包信息
        walletRepository.findByCustomerIdAndWalletType(id, 1)
                .ifPresent(wallet -> vo.setBalance(wallet.getBalance()));
        
        walletRepository.findByCustomerIdAndWalletType(id, 2)
                .ifPresent(wallet -> {
                    vo.setSpecialFundBalance(wallet.getBalance());
                    vo.setFrozenSpecialFund(wallet.getFrozenBalance());
                });
        
        walletRepository.findByCustomerIdAndWalletType(id, 3)
                .ifPresent(wallet -> vo.setPoints(wallet.getBalance()));
        
        // 客户统计信息（累计订单数、累计消费、最近下单时间）
        vo.setTotalOrderAmount(customer.getTotalOrderAmount());
        vo.setTotalOrderCount(customer.getTotalOrderCount());
        vo.setLastOrderTime(customer.getLastOrderTime());

        // 查询赊销统计（当前欠款）
        try {
            CreditDebtVO debtVO = creditApplicationService.statistics(id);
            if (debtVO != null) {
                vo.setTotalDebt(debtVO.getTotalDebt());
            }
        } catch (Exception ex) {
            // 忽略查询赊销统计的异常，避免影响主流程；日志由上层统一记录
        }

        return vo;
    }
    
    /**
     * 分页查询钱包交易记录
     */
    @Transactional(readOnly = true)
    public Page<WalletTransactionVO> queryWalletTransactionPage(WalletTransactionQuery query) {
        Page<WalletTransaction> page = transactionRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getCustomerId(),
                query.getWalletType(),
                query.getTransactionType()
        );
        
        // 获取所有操作人ID
        List<Long> operatorIds = page.getRecords().stream()
                .map(WalletTransaction::getCreatedBy)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        
        // 查询员工信息
        Map<Long, String> staffNameMap = operatorIds.stream()
                .map(staffRepository::findById)
                .filter(opt -> opt.isPresent())
                .collect(Collectors.toMap(
                        opt -> opt.get().getId(),
                        opt -> opt.get().getName(),
                        (v1, v2) -> v1
                ));
        
        // 转换为VO
        List<WalletTransactionVO> voList = page.getRecords().stream()
                .map(transaction -> {
                    WalletTransactionVO vo = new WalletTransactionVO();
                    vo.setId(transaction.getId());
                    vo.setWalletType(transaction.getWalletType());
                    vo.setTransactionType(transaction.getTransactionType());
                    vo.setAmount(transaction.getAmount());
                    vo.setBalanceBefore(transaction.getBalanceBefore());
                    vo.setBalanceAfter(transaction.getBalanceAfter());
                    vo.setReferenceType(transaction.getReferenceType());
                    vo.setReferenceId(transaction.getReferenceId());
                    vo.setRemark(transaction.getRemark());
                    vo.setPaymentMethod(transaction.getPaymentMethod());
                    vo.setCreatedAt(transaction.getCreatedAt());
                    vo.setCreatedByName(staffNameMap.get(transaction.getCreatedBy()));
                    return vo;
                })
                .collect(Collectors.toList());
        
        Page<WalletTransactionVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    /**
     * 创建客户
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerDetailVO createCustomer(CustomerCreateCommand command) {
        Customer customer = registrationService.create(command);

        // 返回详情
        return getCustomerDetail(customer.getId());
    }
    
    /**
     * 分页查询VIP月度权益发放记录（保留用于兼容，但建议使用 queryBenefitLogPage）
     */
    @Transactional(readOnly = true)
    public Page<VipMonthlyRecordVO> queryVipMonthlyRecordPage(VipMonthlyRecordQuery query) {
        Page<VipMonthlyRecord> page = monthlyRecordRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getCustomerId()
        );
        
        // 转换为VO
        List<VipMonthlyRecordVO> voList = page.getRecords().stream()
                .map(record -> {
                    VipMonthlyRecordVO vo = new VipMonthlyRecordVO();
                    vo.setId(record.getId());
                    vo.setCustomerId(record.getCustomerId());
                    vo.setYearMonth(record.getYearMonth());
                    vo.setVipLevel(record.getVipLevel());
                    vo.setRecordType(record.getRecordType() != null ? record.getRecordType() : 1);
                    vo.setGrantTime(record.getGrantTime());
                    vo.setContent(record.getContent() != null ? record.getContent() : "系统自动发放");
                    return vo;
                })
                .collect(Collectors.toList());
        
        Page<VipMonthlyRecordVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    /**
     * 分页查询客户权益流水
     */
    @Transactional(readOnly = true)
    public Page<CustomerBenefitLogVO> queryBenefitLogPage(CustomerBenefitLogQuery query) {
        Page<CustomerBenefitLog> page = benefitLogRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getCustomerId()
        );
        
        // 获取所有操作人ID
        List<Long> staffIds = page.getRecords().stream()
                .map(CustomerBenefitLog::getCreatedBy)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        
        // 批量查询员工姓名
        Map<Long, String> staffNameMap = new HashMap<>();
        if (!staffIds.isEmpty()) {
            staffNameMap = staffIds.stream()
                    .map(staffRepository::findById)
                    .filter(opt -> opt.isPresent())
                    .collect(Collectors.toMap(
                            opt -> opt.get().getId(),
                            opt -> opt.get().getName(),
                            (v1, v2) -> v1
                    ));
        }
        
        // 转换为VO
        final Map<Long, String> finalStaffNameMap = staffNameMap;
        List<CustomerBenefitLogVO> voList = page.getRecords().stream()
                .map(log -> {
                    CustomerBenefitLogVO vo = new CustomerBenefitLogVO();
                    vo.setId(log.getId());
                    vo.setCustomerId(log.getCustomerId());
                    vo.setSourceType(log.getSourceType());
                    vo.setBenefitContent(log.getBenefitContent());
                    vo.setAssociatedId(log.getAssociatedId());
                    vo.setCreatedBy(log.getCreatedBy());
                    vo.setCreatedByName(log.getCreatedBy() != null && log.getCreatedBy() > 0 
                            ? finalStaffNameMap.getOrDefault(log.getCreatedBy(), "未知") 
                            : null);
                    vo.setCreateTime(log.getCreatedAt());
                    return vo;
                })
                .collect(Collectors.toList());
        
        Page<CustomerBenefitLogVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
    
    /**
     * 更新客户
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerDetailVO updateCustomer(Long id, CustomerUpdateCommand command) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        
        if (command.getName() != null) {
            customer.setName(command.getName());
        }
        if (command.getIdCard() != null) {
            customer.setIdCard(command.getIdCard());
        }
        if (command.getAddress() != null) {
            customer.setAddress(command.getAddress());
        }
        if (command.getBindStaffId() != null) {
            customer.setBindStaffId(command.getBindStaffId());
        }
        if (command.getRemark() != null) {
            customer.setRemark(command.getRemark());
        }
        customer.setUpdatedAt(LocalDateTime.now());
        customer.setUpdatedBy(command.getUpdatedBy());
        
        customerRepository.save(customer);
        
        return getCustomerDetail(id);
    }
    
    /**
     * 删除客户（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustomer(Long id) {
        // 验证是否可以删除
        customerDomainService.validateDelete(id);
        
        // 执行逻辑删除
        customerRepository.logicDelete(id);
    }
    
    /**
     * 分页查询客户优惠券列表
     */
    @Transactional(readOnly = true)
    public Page<CustomerCouponVO> queryCustomerCouponPage(CustomerCouponQuery query) {
        Page<CustomerCoupon> page = couponRepository.findPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getCustomerId(),
                query.getStatus()
        );
        
        // 获取所有分类ID
        List<Long> categoryIds = page.getRecords().stream()
                .map(CustomerCoupon::getLimitCategoryId)
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
        List<CustomerCouponVO> voList = page.getRecords().stream()
                .map(coupon -> {
                    CustomerCouponVO vo = new CustomerCouponVO();
                    vo.setId(coupon.getId());
                    vo.setCustomerId(coupon.getCustomerId());
                    vo.setName(coupon.getName());
                    vo.setAmount(coupon.getAmount());
                    vo.setMinOrderAmount(coupon.getMinOrderAmount());
                    vo.setStartTime(coupon.getStartTime());
                    vo.setEndTime(coupon.getEndTime());
                    vo.setStatus(coupon.getStatus());
                    vo.setLimitCategoryId(coupon.getLimitCategoryId());
                    vo.setLimitCategoryName(coupon.getLimitCategoryId() != null 
                            ? finalCategoryNameMap.getOrDefault(coupon.getLimitCategoryId(), null) 
                            : null);
                    vo.setLimitProductId(coupon.getLimitProductId());
                    vo.setUsedOrderId(coupon.getUsedOrderId());
                    vo.setUsedTime(coupon.getUsedTime());
                    vo.setCreatedAt(coupon.getCreatedAt());
                    return vo;
                })
                .collect(Collectors.toList());
        
        Page<CustomerCouponVO> result = new Page<>();
        result.setRecords(voList);
        result.setTotalRow(page.getTotalRow());
        result.setPageNumber(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPage(page.getTotalPage());
        
        return result;
    }
}

