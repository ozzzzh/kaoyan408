package com.kaoyan408.controller;

import com.kaoyan408.config.AccountProperties;
import com.kaoyan408.core.AccountStateCache;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.ClientIpResolver;
import com.kaoyan408.core.JwtUtil;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.MailCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final MailCodeService mailCodeService;
    private final JwtUtil jwtUtil;
    private final AccountStateCache accountStateCache;
    private final AccountProperties accountProperties;

    public AuthController(AuthService authService, MailCodeService mailCodeService, JwtUtil jwtUtil,
                          AccountStateCache accountStateCache, AccountProperties accountProperties) {
        this.authService = authService;
        this.mailCodeService = mailCodeService;
        this.jwtUtil = jwtUtil;
        this.accountStateCache = accountStateCache;
        this.accountProperties = accountProperties;
    }

    public record LoginRequest(@NotBlank(message = "不能为空") String username,
                               @NotBlank(message = "不能为空") String password) {
    }

    public record SendMailCodeRequest(@NotBlank(message = "不能为空")
                                      @Email(message = "格式不正确") String email) {
    }

    /**
     * 注册请求。字段长度只做「上限」兜底，具体规则在 AuthService 里判断，
     * 避免同一套规则在两个地方各写一遍、改一处漏一处。
     */
    public record RegisterRequest(@NotBlank(message = "不能为空")
                                  @Size(max = 50, message = "长度不能超过 50 位") String username,
                                  @NotBlank(message = "不能为空")
                                  @Email(message = "格式不正确") String email,
                                  @NotBlank(message = "不能为空")
                                  @Size(max = 128, message = "长度不能超过 128 位") String password,
                                  @NotBlank(message = "不能为空")
                                  @Size(min = 6, max = 6, message = "验证码为 6 位数字") String code) {
    }

    /**
     * 发送邮箱验证码。
     *
     * <p>purpose 参数区分用途（默认 register）：
     * <ul>
     *   <li>register：匿名可调，发给请求里填的邮箱；</li>
     *   <li>reset-password：必须已登录，且**只用当前账号自己的邮箱**——
     *       否则这个接口就成了给任意地址发信的通道。</li>
     * </ul>
     * 演示账号不允许改密码，所以也不会给它发这类验证码。
     */
    @PostMapping("/auth/send-mail-code")
    public Result<Map<String, Object>> sendMailCode(@Valid @RequestBody SendMailCodeRequest request,
                                                    @RequestParam(required = false) String purpose,
                                                    Authentication authentication,
                                                    HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        String usePurpose = MailCodeService.safePurpose(purpose);
        String target = request.email();
        if (MailCodeService.PURPOSE_RESET_PASSWORD.equals(usePurpose)) {
            if (authentication == null) {
                throw new BusinessException(401, "请先登录后再获取验证码。");
            }
            User current = authService.findByUsername(authentication.getName());
            if (current == null) {
                throw new BusinessException(401, "认证失败，无法访问系统资源。");
            }
            if (isDemoAccount(current.getUsername())) {
                throw new BusinessException(403, DEMO_PASSWORD_DENIED);
            }
            if (current.getEmail() == null || current.getEmail().isBlank()) {
                throw new BusinessException(400, "该账号未绑定邮箱，无法通过验证码修改密码，请联系管理员重置。");
            }
            target = current.getEmail();
        }
        int cooldown = mailCodeService.send(target, usePurpose, ip);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cooldownSeconds", cooldown);
        // 让前端能如实提示：mock 模式下不真的发信，验证码在服务端日志里。
        // 演示与答辩时这一点必须说清楚，否则用户会一直等一封不会来的邮件。
        payload.put("mock", mailCodeService.isMockMode());
        payload.put("purpose", usePurpose);
        payload.put("message", mailCodeService.isMockMode()
                ? "验证码已生成（当前为演示模式，验证码打印在服务端日志）"
                : "验证码已发送，请查收邮件");
        return Result.ok(payload);
    }

    /**
     * 演示账号列表（匿名可读）：登录页据此渲染「以演示用户登录 / 以演示管理员登录」。
     *
     * <p>**刻意连密码一起返回**：这两个都是公开的展示账号，密码本来就写在部署配置里、
     * 也本来就要展示在登录页上；让接口返回而不是前端写死，是为了运维改了口令后
     * 登录页不会继续显示旧密码。
     *
     * <p>边界：只有这两个账号会出现在响应里。真管理员的密码永远不经过任何接口取回。
     * 演示管理员默认只读（写操作在 AdminService 里被拒），所以把它公开是可接受的。
     */
    @GetMapping("/auth/demo-info")
    public Result<Map<String, Object>> demoInfo() {
        List<Map<String, Object>> accounts = new ArrayList<>();
        AccountProperties.Demo demo = accountProperties.getDemo();
        if (demo.isEnabled()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", "user");
            item.put("label", "演示用户");
            item.put("desc", "有做题与背诵数据，能看工作台与看板");
            item.put("username", demo.getUsername());
            item.put("password", demo.getPassword());
            accounts.add(item);
        }
        AccountProperties.DemoAdmin demoAdmin = accountProperties.getDemoAdmin();
        if (demoAdmin.isEnabled()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", "admin");
            item.put("label", "演示管理员");
            item.put("desc", demoAdmin.isReadOnly() ? "可查看后台概览与账号列表（只读）" : "可操作后台");
            item.put("username", demoAdmin.getUsername());
            item.put("password", demoAdmin.getPassword());
            item.put("readOnly", demoAdmin.isReadOnly());
            accounts.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accounts", accounts);
        return Result.ok(data);
    }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request.username(), request.password());
        return Result.ok(tokenPayload(user));
    }

    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
                request.username(), request.email(), request.password(), request.code());
        return Result.ok(tokenPayload(user));
    }

    @GetMapping("/user/current")
    public Result<Map<String, Object>> current(Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }
        User user = authService.requireByUsername(authentication.getName());
        return Result.ok(profile(user));
    }

    /** 演示账号（demo / demo-admin）不允许改密码：它们是公开账号。 */
    private static final String DEMO_PASSWORD_DENIED =
            "演示账号不允许修改密码：它是公开展示账号，改了之后所有人都登不进去。"
                    + "想拥有自己的账号请注册一个（或让管理员重置）。";

    /** 当前用户名是否是公开的演示账号。 */
    private boolean isDemoAccount(String username) {
        if (username == null) {
            return false;
        }
        AccountProperties.Demo demo = accountProperties.getDemo();
        AccountProperties.DemoAdmin demoAdmin = accountProperties.getDemoAdmin();
        return (demo.isEnabled() && username.equals(demo.getUsername()))
                || (demoAdmin.isEnabled() && username.equals(demoAdmin.getUsername()));
    }

    /**
     * 取真实客户端 IP。
     *
     * <p>原来是「优先 X-Forwarded-For 第一段」——可被请求头伪造，发信限流形同虚设。
     * 现在统一走 ClientIpResolver（只信 nginx 无条件覆盖的 X-Real-IP）。
     */
    public record ProfileRequest(String displayName, String avatar) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword, String code) {
    }

    /** 修改资料（昵称 / 头像）。演示账号不允许改。 */
    @PatchMapping("/user/profile")
    public Result<Map<String, Object>> updateProfile(@RequestBody ProfileRequest request,
                                                    Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }
        if (isDemoAccount(authentication.getName())) {
            throw new BusinessException(403, "演示账号的资料已锁定（它是公开展示账号），请注册自己的账号再修改。");
        }
        User updated = authService.updateProfile(
                authentication.getName(), request.displayName(), request.avatar());
        return Result.ok(profile(updated));
    }

    /**
     * 修改密码。
     *
     * <p>成功后旧 token 全部失效（token_version + 1），响应里返回**新 token**，
     * 让当前这个会话可以无缝继续用，不至于改完密码就被踢出去。
     */
    @PostMapping("/user/password")
    public Result<Map<String, Object>> changePassword(@RequestBody PasswordChangeRequest request,
                                                      Authentication authentication) {
        if (authentication == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }
        if (isDemoAccount(authentication.getName())) {
            throw new BusinessException(403, DEMO_PASSWORD_DENIED);
        }
        User updated = authService.changePassword(
                authentication.getName(), request.currentPassword(), request.newPassword(), request.code());
        return Result.ok(tokenPayload(updated));
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }

    private Map<String, Object> tokenPayload(User user) {
        // 先让账号状态缓存失效：否则这次签发的新 ver 会撞上缓存里的旧 ver，
        // 用户刚登录就被判成「令牌已失效」，直到缓存过期（最多 60 秒）。
        accountStateCache.evict(user.getUsername());
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String token = jwtUtil.sign(user.getUsername(), user.getEmail(), version);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("expiresAt", jwtUtil.expiresAt(token));
        payload.put("expiresIn", jwtUtil.getExpireSeconds());
        payload.put("user", profile(user));
        return payload;
    }

    private Map<String, Object> profile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("isAdmin", user.getIsAdmin() != null && user.getIsAdmin() == 1);
        // 展示名：没设昵称时退回 username，前端不用再判空
        profile.put("displayName",
                user.getDisplayName() == null || user.getDisplayName().isBlank()
                        ? user.getUsername() : user.getDisplayName());
        profile.put("avatar", user.getAvatar() == null ? "auto" : user.getAvatar());
        profile.put("demo", isDemoAccount(user.getUsername()));
        profile.put("hasEmail", user.getEmail() != null && !user.getEmail().isBlank());
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }
}