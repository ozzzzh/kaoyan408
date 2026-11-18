package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.config.AccountProperties;
import com.kaoyan408.core.AccountStateCache;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员功能：概览统计与账号管理。
 *
 * <p>权限在 SecurityConfig 用 {@code hasRole("ADMIN")} 拦住，这里只负责业务规则。
 * 三条规则值得单独说明：
 * <ol>
 *   <li><b>不能操作自己</b>：禁用/降权会把管理员自己锁在门外，且线上没人能救；
 *   <li><b>不能降权最后一个管理员</b>：否则整个站点再没有管理员；
 *   <li><b>禁用/改密要顺手加 token_version</b>：不然后台点了「禁用」，
 *       对方手里的 token 还能用到过期（默认 7 天）。
 * </ol>
 */
@Service
public class AdminService {

    /** 重置密码时的随机密码字符集：去掉了 0/O、1/l 这类易混字符。 */
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATED_PASSWORD_LENGTH = 10;

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AccountStateCache accountStateCache;
    private final AccountProperties accountProperties;
    private final SecureRandom random = new SecureRandom();

    public AdminService(UserMapper userMapper, JdbcTemplate jdbcTemplate,
                        PasswordEncoder passwordEncoder, AccountStateCache accountStateCache,
                        AccountProperties accountProperties) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.accountStateCache = accountStateCache;
        this.accountProperties = accountProperties;
    }

    /** 站点概览：账号、内容、练习、AI 的规模数据。 */
    public Map<String, Object> overview(String operatorUsername) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("readOnly", isReadOnlyDemo(operatorUsername));
        data.put("users", count("SELECT COUNT(*) FROM app_user"));
        data.put("activeUsers", count("SELECT COUNT(*) FROM app_user WHERE is_active = 1"));
        data.put("admins", count("SELECT COUNT(*) FROM app_user WHERE is_admin = 1"));
        data.put("newUsers7d", count("SELECT COUNT(*) FROM app_user WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)"));
        data.put("attempts", count("SELECT COUNT(*) FROM question_attempt"));
        // 今天的练习量：heatmap 与「今日待办」的可信来源
        data.put("attemptsToday", count("SELECT COUNT(*) FROM question_attempt WHERE answered_at >= CURDATE()"));
        data.put("questions", count("SELECT COUNT(*) FROM question"));
        data.put("knowledgePoints", count("SELECT COUNT(*) FROM knowledge_point"));
        data.put("knowledgeBlocks", count("SELECT COUNT(*) FROM knowledge_block"));
        data.put("memoryCards", count("SELECT COUNT(*) FROM memory_card"));
        data.put("mockSessions", count("SELECT COUNT(*) FROM mock_session"));
        data.put("aiMessages", count("SELECT COUNT(*) FROM ai_message"));
        data.put("aiConversations", count("SELECT COUNT(*) FROM ai_conversation"));
        return data;
    }

    /**
     * 账号列表：带练习量与最近活跃时间，便于判断「这个号是不是僵尸号」。
     *
     * @param keyword 用户名/邮箱模糊匹配，可为空
     */
    public Map<String, Object> users(String keyword, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;

        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where = " WHERE u.username LIKE ? OR u.email LIKE ? ";
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user u" + where, Integer.class, params.toArray());
        params.add(safeSize);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.email, u.is_active, u.is_admin, u.created_at, u.token_version, "
                        + "  (SELECT COUNT(*) FROM question_attempt a WHERE a.user_id = u.id) AS attempts, "
                        + "  (SELECT COUNT(*) FROM memory_card c WHERE c.user_id = u.id) AS cards, "
                        + "  (SELECT MAX(a2.answered_at) FROM question_attempt a2 WHERE a2.user_id = u.id) AS last_active "
                        + "FROM app_user u" + where
                        + " ORDER BY u.is_admin DESC, u.id ASC LIMIT ? OFFSET ?",
                params.toArray());

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("username", row.get("username"));
            item.put("email", row.get("email"));
            item.put("active", toInt(row.get("is_active")) == 1);
            item.put("admin", toInt(row.get("is_admin")) == 1);
            item.put("createdAt", row.get("created_at"));
            item.put("attempts", toInt(row.get("attempts")));
            item.put("cards", toInt(row.get("cards")));
            item.put("lastActiveAt", row.get("last_active"));
            items.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total == null ? 0 : total);
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("items", items);
        return payload;
    }

    /** 启用 / 禁用账号。禁用会立刻让该用户的 token 失效。 */
    @Transactional
    public Map<String, Object> setActive(String operatorUsername, Long operatorId, Long userId, boolean active) {
        requireWritable(operatorUsername);
        User user = requireUser(userId);
        if (user.getId().equals(operatorId) && !active) {
            throw new BusinessException(400, "不能禁用你自己的账号。");
        }
        user.setIsActive(active ? 1 : 0);
        if (!active) {
            bumpVersion(user);
        }
        userMapper.updateById(user);
        evict(user.getUsername());
        return describe(user, active ? "账号已启用" : "账号已禁用，该用户需要重新登录");
    }

    /** 授予 / 撤销管理员。 */
    @Transactional
    public Map<String, Object> setAdmin(String operatorUsername, Long operatorId, Long userId, boolean admin) {
        requireWritable(operatorUsername);
        User user = requireUser(userId);
        if (user.getId().equals(operatorId) && !admin) {
            throw new BusinessException(400, "不能撤销自己的管理员权限。");
        }
        if (!admin && user.getIsAdmin() != null && user.getIsAdmin() == 1 && adminCount() <= 1) {
            throw new BusinessException(400, "至少要保留一个管理员账号。");
        }
        user.setIsAdmin(admin ? 1 : 0);
        // 权限位走缓存（60 秒），这里主动失效让它立刻生效
        userMapper.updateById(user);
        evict(user.getUsername());
        return describe(user, admin ? "已授予管理员权限" : "已撤销管理员权限");
    }

    /**
     * 重置密码。不传新密码时生成一个随机密码并在响应里返回一次。
     *
     * <p>重置后 token_version 加一：旧 token 立即失效，
     * 这正是「账号可能被盗」时该有的效果。
     */
    @Transactional
    public Map<String, Object> resetPassword(String operatorUsername, Long userId, String newPassword) {
        requireWritable(operatorUsername);
        User user = requireUser(userId);
        String password = (newPassword == null || newPassword.isBlank()) ? generatePassword() : newPassword;
        if (password.length() < 8) {
            throw new BusinessException(400, "新密码至少 8 位。");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        bumpVersion(user);
        userMapper.updateById(user);
        evict(user.getUsername());

        Map<String, Object> payload = describe(user, "密码已重置，该用户所有登录态已失效");
        // 只在生成模式下回显，避免把管理员自己设置的密码留在响应里
        if (newPassword == null || newPassword.isBlank()) {
            payload.put("generatedPassword", password);
        }
        return payload;
    }

    /** 清空某个账号的练习数据（用于「演示账号复位」）。 */
    @Transactional
    public Map<String, Object> clearUserData(String operatorUsername, Long userId) {
        requireWritable(operatorUsername);
        User user = requireUser(userId);
        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("attempts", jdbcTemplate.update("DELETE FROM question_attempt WHERE user_id = ?", userId));
        removed.put("blockProgress", jdbcTemplate.update("DELETE FROM block_progress WHERE user_id = ?", userId));
        removed.put("dailyStats", jdbcTemplate.update("DELETE FROM daily_stat WHERE user_id = ?", userId));
        removed.put("memoryCards", jdbcTemplate.update("DELETE FROM memory_card WHERE user_id = ?", userId));
        removed.put("mockAnswers", jdbcTemplate.update(
                "DELETE FROM mock_answer WHERE session_id IN (SELECT id FROM mock_session WHERE user_id = ?)", userId));
        removed.put("mockSessions", jdbcTemplate.update("DELETE FROM mock_session WHERE user_id = ?", userId));
        Map<String, Object> payload = describe(user, "该账号的练习数据已清空");
        payload.put("removed", removed);
        return payload;
    }

    /**
     * 只读演示管理员的写操作拦截。
     *
     * <p>为什么在服务端而不是只在前端灰掉按钮：演示管理员的密码是公开的，
     * 任何人都能拿它直接调接口。前端禁用只是提示，这里才是真正的门禁。
     */
    private void requireWritable(String operatorUsername) {
        if (isReadOnlyDemo(operatorUsername)) {
            throw new BusinessException(403, "演示管理员为只读模式，不能修改任何数据。");
        }
    }

    /** 当前操作者是否是「只读的演示管理员」。 */
    public boolean isReadOnlyDemo(String username) {
        if (username == null) {
            return false;
        }
        AccountProperties.DemoAdmin demoAdmin = accountProperties.getDemoAdmin();
        boolean isSeparateDemoAdmin = demoAdmin.isEnabled()
                && demoAdmin.getUsername() != null
                && demoAdmin.getUsername().equals(username)
                && demoAdmin.isReadOnly();
        // 合并后的演示账号（demo 同时是管理员）也一律只读：
        // 它是公开账号，能改数据等于把后台开放给全网
        AccountProperties.Demo demo = accountProperties.getDemo();
        boolean isMergedDemo = demo.isEnabled()
                && demo.getUsername() != null
                && demo.getUsername().equals(username);
        return isSeparateDemoAdmin || isMergedDemo;
    }
    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "账号不存在：" + userId);
        }
        return user;
    }

    private void bumpVersion(User user) {
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
    }

    private void evict(String username) {
        accountStateCache.evict(username);
    }

    private int adminCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE is_admin = 1 AND is_active = 1", Integer.class);
        return count == null ? 0 : count;
    }

    private Map<String, Object> describe(User user, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("active", user.getIsActive() != null && user.getIsActive() == 1);
        payload.put("admin", user.getIsAdmin() != null && user.getIsAdmin() == 1);
        payload.put("message", message);
        return payload;
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private int toInt(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}
