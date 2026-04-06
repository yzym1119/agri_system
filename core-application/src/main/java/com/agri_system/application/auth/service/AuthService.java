package com.agri_system.application.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.auth.dto.ClientWechatLoginCommand;
import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.dto.StaffLoginCommand;
import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 认证服务
 * 
 * @author AI Generated
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final CustomerIdentityRepository identityRepository;
    private final CustomerWalletRepository walletRepository;
    private final WechatService wechatService;
    private final SysRoleRepository roleRepository;
    
    /**
     * 员工登录
     * 
     * @param command 登录命令
     * @return 登录结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO staffLogin(StaffLoginCommand command) {
        // 1. 查询员工
        Staff staff = staffRepository.findByCodeOrPhone(command.getAccount())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        
        // 2. 校验密码
        if (staff.getPassword() == null || 
            !BCryptUtils.matches(command.getPassword(), staff.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        
        // 3. 校验状态
        if (!staff.isActive()) {
            throw new IllegalArgumentException("账号已停用");
        }
        
        // 4. 执行登录（使用前缀区分）
        String loginId = "STAFF_" + staff.getId();
        StpUtil.login(loginId);
        String tokenValue = StpUtil.getTokenValue();
        
        // 5. 构建返回结果
        LoginResultVO result = new LoginResultVO();
        result.setTokenName(StpUtil.getTokenName());
        result.setTokenValue(tokenValue);
        
        LoginResultVO.UserInfo userInfo = new LoginResultVO.UserInfo();
        userInfo.setId(staff.getId());
        userInfo.setName(staff.getName());
        // 从sys_staff_role表获取角色代码（使用细分的RBAC权限管理）
        List<String> roleCodes = roleRepository.findRoleCodesByStaffId(staff.getId());
        userInfo.setRole(roleCodes.isEmpty() ? null : roleCodes.get(0)); // 返回第一个角色代码，用于显示
        
        Map<String, Object> extra = new HashMap<>();
        extra.put("code", staff.getCode());
        extra.put("phone", staff.getPhone());
        extra.put("storeId", staff.getStoreId());
        userInfo.setExtra(extra);
        
        result.setUserInfo(userInfo);
        
        return result;
    }
    
    /**
     * 客户微信登录
     * 
     * @param command 微信登录命令
     * @return 登录结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO clientWechatLogin(ClientWechatLoginCommand command) {
        // 1. 调用微信API获取OpenID
        WechatService.WechatLoginResult wechatResult = wechatService.jscode2session(command.getCode());
        String openId = wechatResult.getOpenId();
        String sessionKey = wechatResult.getSessionKey();
        
        // 2. 查询是否已存在该OpenID的身份
        CustomerIdentity identity = identityRepository.findByTypeAndValue("WECHAT_OPENID", openId)
                .orElse(null);
        
        Customer customer;
        
        if (identity != null) {
            // 3. 已存在：获取客户信息
            customer = customerRepository.findById(identity.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
            
            // 更新SessionKey
            identity.setSessionKey(sessionKey);
            identity.setUpdatedAt(LocalDateTime.now());
            identityRepository.save(identity);
        } else {
            // 4. 不存在：自动注册
            customer = createCustomerFromWechat(openId, sessionKey);
        }
        
        // 5. 校验客户状态
        if (customer.getStatus() == null || customer.getStatus() != 1) {
            throw new IllegalArgumentException("客户账号已停用");
        }
        
        // 6. 执行登录（使用前缀区分）
        String loginId = "CUST_" + customer.getId();
        StpUtil.login(loginId);
        String tokenValue = StpUtil.getTokenValue();
        
        // 7. 构建返回结果
        LoginResultVO result = new LoginResultVO();
        result.setTokenName(StpUtil.getTokenName());
        result.setTokenValue(tokenValue);
        
        LoginResultVO.UserInfo userInfo = new LoginResultVO.UserInfo();
        userInfo.setId(customer.getId());
        userInfo.setName(customer.getName());
        userInfo.setRole(customer.getType() != null && customer.getType() == 2 ? "VIP" : "NORMAL");
        
        Map<String, Object> extra = new HashMap<>();
        extra.put("code", customer.getCode());
        extra.put("phone", customer.getPhone());
        extra.put("vipLevel", customer.getVipLevel());
        userInfo.setExtra(extra);
        
        result.setUserInfo(userInfo);
        
        return result;
    }
    
    /**
     * 从微信创建客户（自动注册）
     * 
     * @param openId 微信OpenID
     * @param sessionKey 微信SessionKey
     * @return 客户
     */
    private Customer createCustomerFromWechat(String openId, String sessionKey) {
        // 1. 创建客户
        Customer customer = new Customer();
        customer.setCode("CUST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        customer.setName("微信用户_" + openId.substring(0, 8));
        customer.setPhone(""); // 微信登录时手机号为空，后续可绑定
        customer.setType(1); // 普通客户
        customer.setStatus(1); // 正常
        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        customer = customerRepository.save(customer);
        
        // 2. 创建客户身份
        CustomerIdentity identity = new CustomerIdentity();
        identity.setCustomerId(customer.getId());
        identity.setIdentityType("WECHAT_OPENID");
        identity.setIdentityValue(openId);
        identity.setSessionKey(sessionKey);
        identity.setStatus(1);
        identity.setCreatedAt(LocalDateTime.now());
        identity.setUpdatedAt(LocalDateTime.now());
        identityRepository.save(identity);
        
        // 3. 初始化三钱包
        for (int walletType = 1; walletType <= 3; walletType++) {
            CustomerWallet wallet = new CustomerWallet();
            wallet.setCustomerId(customer.getId());
            wallet.setWalletType(walletType);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenBalance(BigDecimal.ZERO);
            wallet.setVersion(0);
            wallet.setCreatedAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);
        }
        
        return customer;
    }
    
    /**
     * 员工登出
     */
    public void staffLogout() {
        StpUtil.logout();
    }
    
    /**
     * 客户登出
     */
    public void clientLogout() {
        StpUtil.logout();
    }
    
    /**
     * 获取当前登录员工信息
     * 
     * @return 员工信息
     */
    public LoginResultVO.UserInfo getCurrentStaffInfo() {
        if (!StpUtil.isLogin()) {
            throw new IllegalArgumentException("未登录");
        }
        
        String loginId = StpUtil.getLoginIdAsString();
        if (!loginId.startsWith("STAFF_")) {
            throw new IllegalArgumentException("当前登录用户不是员工");
        }
        
        Long staffId = Long.parseLong(loginId.substring(6));
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        
        LoginResultVO.UserInfo userInfo = new LoginResultVO.UserInfo();
        userInfo.setId(staff.getId());
        userInfo.setName(staff.getName());
        // 从sys_staff_role表获取角色代码（使用细分的RBAC权限管理）
        List<String> roleCodes = roleRepository.findRoleCodesByStaffId(staff.getId());
        userInfo.setRole(roleCodes.isEmpty() ? null : roleCodes.get(0)); // 返回第一个角色代码，用于显示
        
        Map<String, Object> extra = new HashMap<>();
        extra.put("code", staff.getCode());
        extra.put("phone", staff.getPhone());
        extra.put("storeId", staff.getStoreId());
        userInfo.setExtra(extra);
        
        return userInfo;
    }
}

