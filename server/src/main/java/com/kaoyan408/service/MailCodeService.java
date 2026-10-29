package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.VerifyCode;
import com.kaoyan408.mapper.AuthRateLimitMapper;
import com.kaoyan408.mapper.VerifyCodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮箱验证码：发送、校验、限流。
 *
 * <p>两种投递模式，用 {@code kaoyan.mail.mode} 切换：
 * <ul>
 *   <li><b>mock</b>（默认）—— 验证码只打印到服务端日志，不真的发信。
 *       流程、限流、错误次数全部与真实投递一致，适合演示与无邮件服务的环境。</li>
 *   <li><b>smtp</b> —— 真正通过 SMTP 发信。连接参数由 Spring Boot 的
 *       spring.mail.* 自动配置注入，本类不自己拼 Properties。</li>
 * </ul>
 *
 * <p>限流三层，缺一不可：同一邮箱 60 秒冷却、同一邮箱每日上限、同一 IP 每小时上限。
 * 只做其中一层的话，攻击者换个维度就能绕过。
 */
@Service
public class MailCodeService {

    private static final Logger log = LoggerFactory.getLogger(MailCodeService.class);

    private static final String CHANNEL = "email";
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 验证码错误达到该次数即作废，防止穷举 6 位码。 */
    private static final int MAX_VERIFY_ATTEMPTS = 3;

    private final VerifyCodeMapper codeMapper;
    private final AuthRateLimitMapper rateLimitMapper;
    /** Spring Boot 的 spring.mail.* 自动配置提供的发信器；mock 模式下不会被调用。 */
    private final JavaMailSender mailSender;

    private final SecureRandom random = new SecureRandom();

    @Value("${kaoyan.mail.mode:mock}")
    private String mode;

    @Value("${kaoyan.mail.cooldown-seconds:60}")
    private int cooldownSeconds;

    @Value("${kaoyan.mail.daily-limit-per-target:5}")
    private int dailyLimitPerTarget;

    @Value("${kaoyan.mail.hourly-limit-per-ip:10}")
    private int hourlyLimitPerIp;

    @Value("${kaoyan.mail.code-expire-seconds:300}")
    private int codeExpireSeconds;

    /** 发件人地址；留空时回落到 spring.mail.username。 */
    @Value("${kaoyan.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public MailCodeService(VerifyCodeMapper codeMapper, AuthRateLimitMapper rateLimitMapper,
                           JavaMailSender mailSender) {
        this.codeMapper = codeMapper;
        this.rateLimitMapper = rateLimitMapper;
        this.mailSender = mailSender;
    }

    /** 当前是否为 mock 模式（前端可据此给出提示文案）。 */
    public boolean isMockMode() {
        return !"smtp".equalsIgnoreCase(mode);
    }

    /**
     * 发送验证码。
     *
     * @return 冷却秒数，供前端倒计时
     */
    @Transactional
    public int send(String email, String clientIp) {
        String normalized = normalize(email);
        LocalDateTime now = LocalDateTime.now();

        // 1) 冷却：距上次发送不足冷却时间就拒绝。
        //    用 created_at 判断，避免为一个「60 秒」再建一张表。
        VerifyCode existing = codeMapper.selectOne(new QueryWrapper<VerifyCode>()
                .eq("target", normalized).eq("channel", CHANNEL).last("LIMIT 1"));
        if (existing != null && existing.getCreatedAt() != null) {
            long elapsed = Duration.between(existing.getCreatedAt(), now).getSeconds();
            if (elapsed < cooldownSeconds) {
                long wait = cooldownSeconds - elapsed;
                throw new BusinessException(429, "发送过于频繁，请 " + wait + " 秒后再试。");
            }
        }

        // 2) 同一邮箱每日上限
        int daily = incrAndGet("target", normalized, now.format(DAY_FMT));
        if (daily > dailyLimitPerTarget) {
            throw new BusinessException(429, "该邮箱今日发送次数已达上限。");
        }

        // 3) 同一 IP 每小时上限。IP 未知（本地/内网直连）时跳过这一层，
        //    否则所有本地请求会共用一个 key，正常调试也会被拦住。
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equals(clientIp)) {
            int hourly = incrAndGet("ip", clientIp, now.format(HOUR_FMT));
            if (hourly > hourlyLimitPerIp) {
                throw new BusinessException(429, "当前网络发送次数过多，请稍后再试。");
            }
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        codeMapper.upsert(normalized, CHANNEL, code, now.plusSeconds(codeExpireSeconds));

        deliver(normalized, code);
        return cooldownSeconds;
    }

    /**
     * 校验验证码。成功即删除记录（一次性使用）。
     *
     * @throws BusinessException 校验失败，消息可直接展示给用户
     */
    @Transactional
    public void verify(String email, String inputCode) {
        String normalized = normalize(email);
        VerifyCode record = codeMapper.selectOne(new QueryWrapper<VerifyCode>()
                .eq("target", normalized).eq("channel", CHANNEL).last("LIMIT 1"));

        if (record == null) {
            throw new BusinessException(400, "验证码已失效，请重新获取。");
        }
        if (record.getExpiresAt() != null && LocalDateTime.now().isAfter(record.getExpiresAt())) {
            codeMapper.deleteById(record.getId());
            throw new BusinessException(400, "验证码已过期，请重新获取。");
        }
        if (inputCode == null || !inputCode.trim().equals(record.getCode())) {
            int attempts = (record.getAttempts() == null ? 0 : record.getAttempts()) + 1;
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                codeMapper.deleteById(record.getId());
                throw new BusinessException(400, "验证码错误次数过多，请重新获取。");
            }
            record.setAttempts(attempts);
            codeMapper.updateById(record);
            throw new BusinessException(400,
                    "验证码错误，还可尝试 " + (MAX_VERIFY_ATTEMPTS - attempts) + " 次。");
        }

        // 一次性使用：校验通过立刻删除，避免同一个码被重复利用
        codeMapper.deleteById(record.getId());
    }

    /* ---------- 内部 ---------- */

    private int incrAndGet(String scope, String key, String window) {
        rateLimitMapper.increment(scope, key, window);
        Integer value = rateLimitMapper.current(scope, key, window);
        return value == null ? 0 : value;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private void deliver(String email, String code) {
        if (isMockMode()) {
            // 明确打一条显眼日志：演示时从服务器日志取码即可完成注册流程
            log.info("[MOCK MAIL] 邮箱={} 验证码={} 有效期={}秒（未真实投递，取该码完成注册）",
                    email, code, codeExpireSeconds);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String from = mailFrom != null && !mailFrom.isBlank() ? mailFrom : mailUsername;
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(email);
            message.setSubject("考研 408 工作台 · 注册验证码");
            message.setText("你的验证码是：" + code + "\n\n有效期 " + (codeExpireSeconds / 60)
                    + " 分钟。若非本人操作请忽略本邮件。");
            mailSender.send(message);
            log.info("验证码已发送 email={} time={}", email, LocalDateTime.now().format(LOG_FMT));
        } catch (Exception e) {
            log.error("验证码发送失败 email={} error={}", email, e.getMessage());
            throw new BusinessException(500, "验证码发送失败，请稍后重试。");
        }
    }
}