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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final WechatService wechatService;
    private final SysRoleRepository roleRepository;
    private final StaffAuthorityService authorityService;
    private final StringRedisTemplate redisTemplate;

    @Value("${wechat.app-id:}")
    private String wechatAppId;

    /** 开发登录显式开关（默认关闭）；生产环境绝不开启，与 DevClientLoginController 的注册条件保持一致。 */
    @Value("${auth.dev-login.enabled:false}")
    private boolean devLoginEnabled;

    /** prod profile 硬约束：即使配置误开也无法在生产调用开发登录 */
    private final org.springframework.core.env.Environment environment;

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
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public LoginResultVO clientWechatLogin(ClientWechatLoginCommand command) {
        var result=wechatService.jscode2session(command.getCode());
        if(result==null||wechatAppId==null||!wechatAppId.matches("wx[a-zA-Z0-9]{16}")||!wechatAppId.equals(result.getAppId())
                ||result.getOpenId()==null||result.getOpenId().isBlank()||result.getSessionKey()==null||result.getSessionKey().isBlank())
            throw new IllegalStateException("微信登录身份或应用配置无效");
        var identity=identityRepository.findWechatByAppAndOpenIdForUpdate(wechatAppId,result.getOpenId())
                .orElseThrow(()->new IllegalStateException("暂时无法登录，请联系门店核对客户档案；系统不会自动新建空档案"));
        if(!identity.isActive()||identity.getId()==null||!identity.isWechatOpenId()||!wechatAppId.equals(identity.getAppId())
                ||!result.getOpenId().equals(identity.getIdentityValue())||identity.getCustomerId()==null)
            throw new IllegalArgumentException("微信身份已停用或尚未完成关联核验");
        Customer customer=customerRepository.findById(identity.getCustomerId()).filter(AuthService::activeCustomer)
                .orElseThrow(()->new IllegalArgumentException("客户不存在或已停用"));
        if(!identity.getCustomerId().equals(customer.getId()))throw new IllegalArgumentException("客户身份资料待核验");
        identity.setSessionKey(result.getSessionKey());identity.setUpdatedAt(LocalDateTime.now());identityRepository.save(identity);
        StpUtil.login("CUST_"+customer.getId(),new cn.dev33.satoken.stp.SaLoginModel().setToken(UUID.randomUUID().toString()));
        StpUtil.getTokenSession().set("clientAuthV1",Map.of("kind","WECHAT","identityId",identity.getId().toString(),"appId",wechatAppId));
        LoginResultVO login=new LoginResultVO();login.setTokenName(StpUtil.getTokenName());login.setTokenValue(StpUtil.getTokenValue());
        LoginResultVO.UserInfo user=new LoginResultVO.UserInfo();user.setId(customer.getId());user.setName(customer.getName());user.setRole(customer.isVip()?"VIP":"NORMAL");
        Map<String,Object> extra=new HashMap<>();extra.put("code",customer.getCode());extra.put("phone",customer.getPhone());extra.put("vipLevel",customer.getVipLevel());extra.put("bindingState","LINKED");user.setExtra(extra);login.setUserInfo(user);
        return login;
    }

    /**
     * 开发环境客户登录（仅限开发/测试，须显式配置 auth.dev-login.enabled=true）
     * 通过手机号直接登录，绕过微信认证
     */
    public LoginResultVO devClientLogin(String phone) {
        // 硬约束第一道：prod profile 下无论配置如何都拒绝（防运维误开 auth.dev-login.enabled）
        if (environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("开发登录在生产环境被硬性禁止");
        }
        if (!devLoginEnabled) {
            // 防御性兜底：即使误注册了端点，配置未显式开启也直接拒绝。
            throw new IllegalStateException("开发登录未启用：需显式配置 auth.dev-login.enabled=true");
        }
        if (wechatAppId != null && !wechatAppId.isEmpty()) {
            throw new IllegalStateException("开发登录仅在未配置微信appId时可用");
        }

        Customer customer = customerRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("客户不存在: " + phone));

        if (!activeCustomer(customer)) {
            throw new IllegalArgumentException("客户账号已停用");
        }

        String loginId = "CUST_" + customer.getId();
        StpUtil.login(loginId,new cn.dev33.satoken.stp.SaLoginModel().setToken(UUID.randomUUID().toString()));
        StpUtil.getTokenSession().set("clientAuthV1",Map.of("kind","DEV"));

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
        if(!StpUtil.isLogin())throw new IllegalArgumentException("未登录");
        String loginId=StpUtil.getLoginIdAsString();
        if(loginId==null||!loginId.matches("CUST_[1-9]\\d{0,18}"))throw new IllegalArgumentException("当前登录用户不是客户");
        Long customerId;
        try{customerId=Long.valueOf(loginId.substring(5));}catch(NumberFormatException invalid){throw new IllegalArgumentException("客户登录已失效");}
        Object raw=StpUtil.getTokenSession().get("clientAuthV1");
        if(!(raw instanceof Map<?,?> binding))throw new IllegalArgumentException("客户登录需重新核验");
        if("DEV".equals(binding.get("kind"))) {
            if(!devLoginEnabled||(wechatAppId!=null&&!wechatAppId.isEmpty())||(environment!=null&&java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")))
                throw new IllegalArgumentException("开发客户会话已失效");
        } else if("WECHAT".equals(binding.get("kind"))) {
            if(wechatAppId==null||!wechatAppId.matches("wx[a-zA-Z0-9]{16}")||!wechatAppId.equals(binding.get("appId")))throw new IllegalArgumentException("微信应用登录已失效");
            Long identityId;
            try{identityId=Long.valueOf(String.valueOf(binding.get("identityId")));}catch(NumberFormatException invalid){throw new IllegalArgumentException("微信身份登录已失效");}
            if(identityId<=0)throw new IllegalArgumentException("微信身份登录已失效");
            var identity=identityRepository.findById(identityId).orElse(null);
            if(identity==null||!identity.isActive()||!identity.isWechatOpenId()||!wechatAppId.equals(identity.getAppId())||!customerId.equals(identity.getCustomerId()))
                throw new IllegalArgumentException("微信身份已停用或关联已变更，请重新核验");
        } else throw new IllegalArgumentException("客户登录来源无效");
        var customer=customerRepository.findById(customerId).orElseThrow(()->new IllegalArgumentException("客户不存在"));
        if(!activeCustomer(customer))throw new IllegalArgumentException("客户账号已停用");
        if(!customerId.equals(customer.getId()))throw new IllegalArgumentException("客户身份资料待核验");
        return customerId;
    }
    private static boolean activeCustomer(Customer c){return c!=null&&c.isActive()&&!Integer.valueOf(1).equals(c.getDeleted());}
    public record ClientProfile(String customerId,String name,String phoneMasked,String bindingState) {}
    public ClientProfile getCurrentClientProfile(){
        Long id=getCurrentCustomerId();var c=customerRepository.findById(id).filter(AuthService::activeCustomer).orElseThrow(()->new IllegalArgumentException("客户登录已失效"));
        String phone=c.getPhone();String masked=phone==null||phone.isBlank()?null:phone.length()<7?"已登记联系方式":phone.substring(0,3)+"****"+phone.substring(phone.length()-4);
        Object marker=StpUtil.getTokenSession().get("clientAuthV1");
        String state=marker instanceof Map<?,?> m&&"DEV".equals(m.get("kind"))?"DEV_ONLY":"LINKED";
        return new ClientProfile(id.toString(),c.getName(),masked,state);
    }
}
