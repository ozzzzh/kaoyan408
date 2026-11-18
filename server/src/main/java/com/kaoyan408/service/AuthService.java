package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 账号相关业务：注册、登录、按用户名取用户。
 *
 * <p>校验放在这里而不是只靠前端：前端提示只是体验，服务端校验才是约束。
 * 之前注册接口不校验任何格式，密码填 1 位也能建号。
 */
@Service
public class AuthService {

    /** 用户名：2-50 位，字母/数字/下划线/中文。放宽到中文是为了允许「张三」这类用户名。 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5]{2,50}$");

    /** 邮箱：结构校验即可，不追求 RFC 全集。真正有效性靠收验证码证明。 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;

    /** 登录失败多少次后锁定。 */
    private static final int LOGIN_MAX_FAILURES = 5;

    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final MailCodeService mailCodeService;
    private final RateLimitService rateLimitService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       MailCodeService mailCodeService, RateLimitService rateLimitService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailCodeService = mailCodeService;
        this.rateLimitService = rateLimitService;
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("LIMIT 1"));
    }

    public User findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userMapper.selectOne(
                new QueryWrapper<User>().eq("email", email.trim().toLowerCase()).last("LIMIT 1"));
    }

    /**
     * 登录。连续失败达到阈值会在一小时内锁定该用户名。
     *
     * <p>原实现没有失败限流，密码可被无限次尝试。这里按「用户名 + 小时窗」计数，
     * 成功登录后清零。
     */
    @Transactional
    public User login(String username, String password) {
        String account = username == null ? "" : username.trim();
        String window = LocalDateTime.now().format(WINDOW_FMT);
        String failKey = "login:" + account.toLowerCase();

        int failures = rateLimitService.current("target", failKey, window);
        if (failures >= LOGIN_MAX_FAILURES) {
            throw new BusinessException(429, "密码错误次数过多，请稍后再试。");
        }

        User user = findByUsername(account);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            // 计数走独立事务（RateLimitService 用 REQUIRES_NEW）：
            // 下面紧接着要抛 BusinessException，本方法的 @Transactional 会回滚，
            // 若计数与业务同事务就会被一起抹掉，限流永远不生效。
            int now = rateLimitService.increment("target", failKey, window);
            int left = LOGIN_MAX_FAILURES - now;
            if (left <= 0) {
                throw new BusinessException(429, "密码错误次数过多，请稍后再试。");
            }
            // 不区分「用户不存在」与「密码错误」，避免用户名被枚举
            throw new BusinessException(401, "用户名或密码错误。还可尝试 " + left + " 次。");
        }

        requireActive(user);
        // 登录成功清掉失败计数，避免正常用户因历史失败被误锁
        rateLimitService.reset("target", failKey);
        return user;
    }

    /**
     * 注册。必须先通过邮箱验证码。
     *
     * @param code 邮件验证码，校验通过即一次性作废
     */
    @Transactional
    public User register(String username, String email, String password, String code) {
        String name = username == null ? "" : username.trim();
        String mail = email == null ? "" : email.trim().toLowerCase();

        if (!USERNAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(400, "用户名需为 2-50 位的字母、数字、下划线或中文。");
        }
        validatePassword(password);
        if (!EMAIL_PATTERN.matcher(mail).matches()) {
            throw new BusinessException(400, "邮箱格式不正确。");
        }
        if (findByUsername(name) != null) {
            throw new BusinessException(400, "该用户名已被注册。");
        }
        if (findByEmail(mail) != null) {
            throw new BusinessException(400, "该邮箱已被注册。");
        }

        // 验证码放在格式与查重之后校验：格式不对就没必要消耗掉一个有效验证码
        mailCodeService.verify(mail, MailCodeService.PURPOSE_REGISTER, code);

        User user = new User();
        user.setUsername(name);
        user.setEmail(mail);
        user.setPasswordHash(passwordEncoder.encode(password));
        userMapper.insert(user);
        // 必须显式回填：JWT 里要带 ver claim，而 insert 之后的实体不会自动回读数据库默认值。
        // 漏了这一步，刚注册的用户拿到的 token 里 ver=0，与库里的默认值 1 不匹配，
        // 下一个请求就会被判为「令牌已失效」——注册成功却立刻处于未登录状态。
        user.setTokenVersion(1);
        return user;
    }

    /**
     * 修改资料：昵称 + 头像。
     *
     * <p>只允许改「展示层」字段。username 是登录名，被 JWT subject、限流键、
     * 越权校验引用，改名牵连太广，所以不开放。
     */
    public User updateProfile(String username, String displayName, String avatar) {
        User user = requireByUsername(username);
        if (displayName != null) {
            String name = displayName.trim();
            if (name.isEmpty()) {
                throw new BusinessException(400, "昵称不能为空。");
            }
            if (name.codePointCount(0, name.length()) > 20) {
                throw new BusinessException(400, "昵称最多 20 个字符。");
            }
            // 控制字符会破坏列表渲染，直接拒绝
            if (name.chars().anyMatch(Character::isISOControl)) {
                throw new BusinessException(400, "昵称包含不支持的字符。");
            }
            user.setDisplayName(name);
        }
        if (avatar != null) {
            user.setAvatar(normalizeAvatar(avatar));
        }
        userMapper.updateById(user);
        return user;
    }

    /**
     * 修改密码。
     *
     * <p>三道校验：当前密码、新密码强度、邮箱验证码（用途必须是 reset-password）。
     * 成功后 token_version + 1：**其它设备全部掉线**，调用方用返回的新 token 继续用。
     *
     * <p>不在这里判断"是否演示账号"——那属于账号策略，放在 Controller 层更清楚。
     */
    @Transactional
    public User changePassword(String username, String currentPassword, String newPassword, String code) {
        User user = requireByUsername(username);

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "当前密码不正确。");
        }
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "新密码不能与当前密码相同。");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(400, "该账号未绑定邮箱，无法通过验证码修改密码，请联系管理员重置。");
        }
        mailCodeService.verify(user.getEmail(), MailCodeService.PURPOSE_RESET_PASSWORD, code);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // 改密即踢掉所有旧会话
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        return user;
    }

    /** 头像只接受 'auto' / 'color:#rrggbb' / 'emoji:<一个字符>'，拒绝任意字符串（避免前端渲染出意料外的东西）。 */
    private String normalizeAvatar(String avatar) {
        String value = avatar.trim();
        if (value.isEmpty() || "auto".equals(value)) {
            return "auto";
        }
        if (value.startsWith("color:")) {
            // 底色：只认 #rrggbb，存小写，避免同一颜色因大小写产生不同取值
            String hex = value.substring("color:".length()).trim();
            if (hex.matches("#[0-9a-fA-F]{6}")) {
                return "color:" + hex.toLowerCase(Locale.ROOT);
            }
            throw new BusinessException(400, "头像颜色不合法。");
        }
        if (value.startsWith("emoji:")) {
            String emoji = value.substring("emoji:".length()).trim();
            int points = emoji.codePointCount(0, emoji.length());
            if (points >= 1 && points <= 2 && emoji.codePointAt(0) > 0x2000) {
                return "emoji:" + emoji;
            }
        }
        throw new BusinessException(400, "头像标识不合法。");
    }

    public User requireByUsername(String username) {
        User user = findByUsername(username);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }
        requireActive(user);
        return user;
    }

    /** 被禁用的账号不允许登录，也不允许继续使用已有 token。 */
    /** 密码规则：长度 8–128，且不能是纯数字（弱口令最常见的一类）。 */
    private void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BusinessException(400, "密码长度需在 " + PASSWORD_MIN + "-" + PASSWORD_MAX + " 位之间。");
        }
        if (password.chars().allMatch(Character::isDigit)) {
            throw new BusinessException(400, "密码不能是纯数字，请混合字母或符号。");
        }
    }

    private void requireActive(User user) {
        if (user.getIsActive() != null && user.getIsActive() == 0) {
            throw new BusinessException(403, "该账号已被禁用，请联系管理员。");
        }
    }
}
