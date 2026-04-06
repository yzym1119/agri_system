package com.agri_system.application.auth.util;

import org.junit.jupiter.api.Test;

/**
 * BCrypt 密码生成测试
 * 用于生成管理员账号的密码哈希值
 * 
 * 运行此测试后，控制台会输出密码哈希值
 * 将输出的哈希值更新到 scripts/init_admin_account.sql 中
 */
public class BCryptPasswordGeneratorTest {

    @Test
    public void generateAdminPassword() {
        String password = "123456";
        String hash = BCryptUtils.encode(password);
        
        System.out.println("============================================");
        System.out.println("密码: " + password);
        System.out.println("BCrypt 哈希值: " + hash);
        System.out.println("============================================");
        System.out.println();
        System.out.println("请将 scripts/init_admin_account.sql 中的密码哈希值更新为:");
        System.out.println(hash);
        System.out.println();
        
        // 验证密码
        boolean matches = BCryptUtils.matches(password, hash);
        System.out.println("验证密码是否匹配: " + (matches ? "✓ 匹配" : "✗ 不匹配"));
        
        assert matches : "密码验证失败";
    }
}



