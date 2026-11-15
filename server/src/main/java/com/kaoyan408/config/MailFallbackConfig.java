package com.kaoyan408.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;

/**
 * 邮件发送器的兜底配置。
 *
 * <p>背景：Spring Boot 只在配置了 {@code spring.mail.host} 时才自动注册 JavaMailSender。
 * 默认部署（mock 模式、未填 host）下这个 bean 不存在，注入会直接让应用启动失败。
 *
 * <p>关键：条件必须写成「仅当未配置 host 时」({@code havingValue="false"})。
 * 早先只写 {@code @ConditionalOnMissingBean} 是错的——用户配置类的解析早于自动配置，
 * 兜底 bean 会先注册，导致 Boot 的 MailSenderAutoConfiguration 因「已存在同名 bean」
 * 而退让；结果是配了 host 也不发信，调用时才报「未配置」。此坑实测踩到过。
 *
 * <p>配置了 {@code spring.mail.host} 后本类整体不生效，由 Boot 自动配置提供真实发信器。
 */
@Configuration
@ConditionalOnProperty(name = "spring.mail.host", havingValue = "false", matchIfMissing = true)
public class MailFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(MailFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender unavailableMailSender(
            @Value("${spring.mail.host:}") String host) {
        log.warn("未配置 spring.mail.host（当前值='{}'），邮件发送不可用；"
                + "注册验证码请使用 mock 模式（MAIL_MODE=mock）或补齐 SPRING_MAIL_* 配置", host);
        return new NoopMailSender();
    }

    /** 一个调用即报错的发送器，避免把「未配置」伪装成「已发送」。 */
    static class NoopMailSender extends JavaMailSenderImpl {

        private static MailPreparationException notConfigured() {
            return new MailPreparationException(
                    "邮件服务未配置：请在环境变量中设置 SPRING_MAIL_HOST / SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD，"
                            + "或改用 mock 模式（MAIL_MODE=mock）。");
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            throw notConfigured();
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            throw notConfigured();
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            throw notConfigured();
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            throw notConfigured();
        }
    }
}