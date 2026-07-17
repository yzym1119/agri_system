package com.agri_system.application.staff.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class StaffSessionOperator {

    public boolean isLogin() {
        return StpUtil.isLogin();
    }

    public String getLoginIdAsString() {
        return StpUtil.getLoginIdAsString();
    }

    public void kickout(String loginId) {
        StpUtil.kickout(loginId);
    }
}
