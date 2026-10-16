package com.agri_system.application.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.auth.dto.ClientWechatLoginCommand;
import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.dto.StaffLoginCommand;
import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.application.staff.service.StaffAuthorityService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final StaffAuthorityService authorityService;
    private final StringRedisTemplate redisTemplate;

    @Value("${wechat.app-id:}")
    private String wechatAppId;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCK_MINUTES = 15;
    
    /**
     * 员工登录
     * 
     * @param command 登录命令
     * @return 登录结果
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO staffLogin(StaffLoginCommand command) {
        String account = command.getAccount();
        String rateLimitKey = "login:fail:" + account;

        // 0. 检查是否被锁定
        String failCountStr = redisTemplate.opsForValue().get(rateLimitKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_ATTEMPTS) {
            Long ttl = redisTemplate.getExpire(rateLimitKey, TimeUnit.MINUTES);
            throw new IllegalArgumentException(
                String.format("登录失败次数过多，账号已锁定，请%d分钟后再试", ttl != null && ttl > 0 ? ttl : LOGIN_LOCK_MINUTES));
        }

        // 1. 查询员工
        Staff staff = staffRepository.findByCodeOrPhone(account)
                .orElseThrow(() -> {
                    recordLoginFailure(rateLimitKey);
                    return new IllegalArgumentException("账号或密码错误");
                });

        // 2. 校验密码
        if (staff.getPassword() == null ||
            !BCryptUtils.matches(command.getPassword(), staff.getPassword())) {
            recordLoginFailure(rateLimitKey);
            throw new IllegalArgumentException("账号或密码错误");
        }

        // 3. 校验状态
        if (!staff.isActive()) {
            throw new IllegalArgumentException("账号已停用");
        }

        // 4. 登录成功，清除失败计数
        redisTemplate.delete(rateLimitKey);

        // 5. 执行登录（使用前缀区分）
        String loginId = "STAFF_" + staff.getId();
        StpUtil.login(loginId);
        String tokenValue = StpUtil.getTokenValue();

        // 6. 构建返回结果
        LoginResultVO result = new LoginResultVO();
        result.setTokenName(StpUtil.getTokenName());
        result.setTokenValue(tokenValue);

        result.setUserInfo(buildStaffUserInfo(staff));

        return result;
    }

    private void recordLoginFailure(String rateLimitKey) {
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
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
     * 开发环境客户登录（仅限开发/测试，生产环境不开放）
     * 通过手机号直接登录，绕过微信认证
     */
    public LoginResultVO devClientLogin(String phone) {
        if (wechatAppId != null && !wechatAppId.isEmpty()) {
            throw new IllegalStateException("开发登录仅在未配置微信appId时可用");
        }

        Customer customer = customerRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在: " + phone));

        if (customer.getStatus() == null || customer.getStatus() != 1) {
            throw new IllegalArgumentException("客户账号已停用");
        }

        String loginId = "CUST_" + customer.getId();
        StpUtil.login(loginId);

        LoginResultVO result = new LoginResultVO();
        result.setTokenName(StpUtil.getTokenName());
        result.setTokenValue(StpUtil.getTokenValue());

        LoginResultVO.UserInfo userInfo = new LoginResultVO.UserInfo();
        userInfo.setId(customer.getId());
        userInfo.setName(customer.getName());
        userInfo.setRole(customer.getType() != null && customer.getType() == 2 ? "VIP" : "NORMAL");
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
        
        return buildStaffUserInfo(staff);
    }

    private LoginResultVO.UserInfo buildStaffUserInfo(Staff staff) {
        LoginResultVO.UserInfo userInfo = new LoginResultVO.UserInfo();
        userInfo.setId(staff.getId());
        userInfo.setName(staff.getName());
        List<String> roleCodes = roleRepository.findRoleCodesByStaffId(staff.getId());
        userInfo.setRole(roleCodes.isEmpty() ? null : roleCodes.get(0));
        userInfo.setPermissions(authorityService.loadPermissions(staff.getId()));

        Map<String, Object> extra = new HashMap<>();
        extra.put("code", staff.getCode());
        extra.put("phone", staff.getPhone());
        extra.put("storeId", staff.getStoreId());
        extra.put("roleCodes", roleCodes);
        userInfo.setExtra(extra);
        return userInfo;
    }

    /**
     * 获取当前登录客户ID
     *
     * @return 客户ID
     */
    public Long getCurrentCustomerId() {
        if (!StpUtil.isLogin()) {
            throw new IllegalArgumentException("未登录");
        }

        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith("CUST_")) {
            throw new IllegalArgumentException("当前登录用户不是客户");
        }

        Long customerId = Long.parseLong(loginId.substring("CUST_".length()));
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        if (!customer.isActive()) {
            throw new IllegalArgumentException("客户账号已停用");
        }
        return customerId;
    }
}
