import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码生成工具
 * 用于生成管理员账号的密码哈希值
 * 
 * 使用方法：
 * 1. 确保项目依赖中包含 spring-security-crypto
 * 2. 运行此类的 main 方法
 * 3. 将输出的哈希值更新到 init_admin_account.sql 中
 */
public class BCryptPasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成 123456 的 BCrypt 哈希
        String password = "123456";
        String hash = encoder.encode(password);
        
        System.out.println("============================================");
        System.out.println("密码: " + password);
        System.out.println("BCrypt 哈希值: " + hash);
        System.out.println("============================================");
        System.out.println();
        System.out.println("请将以下 SQL 语句中的密码哈希值更新为:");
        System.out.println(hash);
        System.out.println();
        System.out.println("验证密码是否匹配:");
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + (matches ? "✓ 匹配" : "✗ 不匹配"));
    }
}

