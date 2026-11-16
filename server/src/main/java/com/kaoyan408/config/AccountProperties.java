package com.kaoyan408.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内置账号配置：演示账号与管理员账号。
 *
 * <p>为什么要自动初始化而不是手工插库：
 * <ul>
 *   <li>演示账号是「给访客登录用的公开账号」，每次重建环境都应该存在，且密码写在配置里，
 *       不该靠人去数据库里改；</li>
 *   <li>管理员账号的密码不能进仓库，所以只从环境变量读；没配就什么都不做（不创建、
 *       不提升任何人），避免出现「默认管理员 + 默认密码」这种更糟的状态。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "kaoyan")
public class AccountProperties {

    private Demo demo = new Demo();
    private Admin admin = new Admin();

    @Data
    public static class Demo {
        /** 是否自动维护演示账号。 */
        private boolean enabled = true;
        private String username = "demo";
        private String password = "demo123456";
        /** 是否给演示账号预置一些做题与背诵数据（首页/看板才有东西可看）。 */
        private boolean seedData = true;
    }

    @Data
    public static class Admin {
        /** 管理员用户名；为空则不做任何自动初始化。 */
        private String username = "";
        /** 管理员初始密码；仅在账号不存在时用于创建。 */
        private String password = "";
        /** 为 true 时每次启动都把管理员密码重置为上面配置的值（忘记密码时用）。 */
        private boolean resetPassword = false;
    }
}
