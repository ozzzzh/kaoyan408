package com.kaoyan408.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.QuestionMapper;
import com.kaoyan408.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置账号初始化：演示账号 + 管理员账号。
 *
 * <p>两件事都要求**幂等**：每次启动跑一遍，已有账号就只对齐状态（启用、管理员位、密码），
 * 不重复创建、不重复灌演示数据。
 *
 * <p>执行时机在内容种子之后（{@code @Order} 让它在 ContentSeedConfig 之后跑），
 * 否则演示账号要关联的题目还不存在。
 */
@Slf4j
@Configuration
public class AccountBootstrap {

    /** 演示账号预置的作答分布：让看板有正确率、错题本有内容，但不至于满屏。 */
    /** 种子版本：改大后下次启动会清掉 demo 的历史数据并重铺（只影响演示账号） */
    private static final int DEMO_SEED_VERSION = 2;
    /** 铺多少条作答：覆盖四科和多个年份，够让看板/错题本/热力图都有内容 */
    private static final int DEMO_ATTEMPTS_TARGET = 220;
    /** 铺多少张背诵卡 */
    private static final int DEMO_CARDS_TARGET = 72;
    /** 铺几次模拟考试 */
    private static final int DEMO_MOCK_SESSIONS = 4;
    /** 连续打卡天数 */
    private static final int DEMO_STREAK_DAYS = 14;
    private static final int DEMO_WRONG = 5;
    private static final int DEMO_CARDS = 6;

    @Bean
    public ApplicationRunner accountBootstrapRunner(UserMapper userMapper,
                                                    QuestionMapper questionMapper,
                                                    PasswordEncoder passwordEncoder,
                                                    JdbcTemplate jdbcTemplate,
                                                    AccountProperties properties) {
        return args -> {
            try {
                ensureAdmin(userMapper, passwordEncoder, properties.getAdmin());
                ensureDemo(userMapper, questionMapper, passwordEncoder, jdbcTemplate, properties.getDemo());
                ensureDemoAdmin(userMapper, passwordEncoder, properties);
            } catch (Exception ex) {
                // 内置账号初始化失败不应该拖住整个服务启动
                log.warn("内置账号初始化失败，服务继续启动：{}", ex.getMessage());
            }
        };
    }

    /**
     * 管理员账号。
     *
     * <p>只从配置读密码（环境变量），仓库里没有任何默认管理员密码。
     * 未配置用户名时直接跳过：宁可没有管理员，也不要出现一个默认口令的后门。
     */
    private void ensureAdmin(UserMapper userMapper, PasswordEncoder encoder, AccountProperties.Admin admin) {
        String username = admin.getUsername();
        if (username == null || username.isBlank()) {
            log.info("未配置管理员账号（KAOYAN_ADMIN_USERNAME 为空），跳过管理员初始化");
            return;
        }
        User existing = findByUsername(userMapper, username);
        if (existing == null) {
            if (admin.getPassword() == null || admin.getPassword().isBlank()) {
                log.warn("管理员 {} 不存在，但未提供 KAOYAN_ADMIN_PASSWORD，无法创建", username);
                return;
            }
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@admin.local");
            user.setPasswordHash(encoder.encode(admin.getPassword()));
            user.setIsActive(1);
            user.setIsAdmin(1);
            user.setTokenVersion(1);
            userMapper.insert(user);
            log.info("已创建管理员账号 {}", username);
            return;
        }

        boolean changed = false;
        if (existing.getIsAdmin() == null || existing.getIsAdmin() != 1) {
            existing.setIsAdmin(1);
            changed = true;
        }
        if (existing.getIsActive() != null && existing.getIsActive() == 0) {
            existing.setIsActive(1);
            changed = true;
        }
        if (admin.isResetPassword()) {
            if (admin.getPassword() == null || admin.getPassword().isBlank()) {
                log.warn("管理员 {} 要求重置密码，但未提供 KAOYAN_ADMIN_PASSWORD", username);
            } else {
                existing.setPasswordHash(encoder.encode(admin.getPassword()));
                // 改密后旧 token 立即失效
                existing.setTokenVersion((existing.getTokenVersion() == null ? 0 : existing.getTokenVersion()) + 1);
                changed = true;
            }
        }
        if (changed) {
            userMapper.updateById(existing);
            log.info("管理员账号 {} 已对齐（管理员位/启用状态/密码）", username);
        } else {
            log.info("管理员账号 {} 已就绪", username);
        }
    }

    /**
     * 演示管理员：公开可登录的后台演示账号。
     *
     * <p>刻意与真管理员分开：它的用户名/密码会公开展示在登录页上，
     * 不能和唯一的真管理员是同一个账号。同时默认只读（见 AccountProperties.DemoAdmin），
     * 写操作在 AdminService 里被拒绝。
     */
    private void ensureDemoAdmin(UserMapper userMapper, PasswordEncoder encoder, AccountProperties properties) {
        AccountProperties.DemoAdmin demoAdmin = properties.getDemoAdmin();
        if (!demoAdmin.isEnabled()) {
            // 已合并到 demo 账号：把历史上可能存在的 demo-admin 停用，避免还能登录
            User legacy = findByUsername(userMapper, demoAdmin.getUsername());
            if (legacy != null && legacy.getIsActive() != null && legacy.getIsActive() == 1
                    && !legacy.getUsername().equals(properties.getDemo().getUsername())) {
                legacy.setIsActive(0);
                userMapper.updateById(legacy);
                log.info("演示管理员已合并到 demo 账号，旧账号 {} 已停用", legacy.getUsername());
            } else {
                log.info("演示管理员已合并到 demo 账号（kaoyan.demo-admin.enabled=false）");
            }
            return;
        }
        String username = demoAdmin.getUsername();
        String realAdmin = properties.getAdmin().getUsername();
        if (realAdmin != null && !realAdmin.isBlank() && realAdmin.equals(username)) {
            // 同名会把真管理员变成公开账号，这是必须挡住的配置错误
            log.error("演示管理员用户名与真管理员相同（{}），已跳过演示管理员初始化。"
                    + "请把 KAOYAN_DEMO_ADMIN_USERNAME 改成其它名字。", username);
            return;
        }
        User existing = findByUsername(userMapper, username);
        if (existing == null) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@demo.local");
            user.setPasswordHash(encoder.encode(demoAdmin.getPassword()));
            user.setIsActive(1);
            user.setIsAdmin(1);
            user.setTokenVersion(1);
            userMapper.insert(user);
            log.info("已创建演示管理员 {}（只读={}）", username, demoAdmin.isReadOnly());
            return;
        }
        boolean changed = false;
        if (existing.getIsAdmin() == null || existing.getIsAdmin() != 1) {
            existing.setIsAdmin(1);
            changed = true;
        }
        if (existing.getIsActive() != null && existing.getIsActive() == 0) {
            existing.setIsActive(1);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(existing);
            log.info("演示管理员 {} 已对齐为管理员/启用", username);
        } else {
            log.info("演示管理员 {} 已就绪（只读={}）", username, demoAdmin.isReadOnly());
        }
    }

    /**
     * 演示账号：公开可登录，用于让访客直接看到「有数据的样子」。
     *
     * <p>密码写在配置里是刻意的（它本来就是公开账号），但仍然支持用环境变量覆盖，
     * 避免线上被写死在代码里的口令拖累。
     */
    private void ensureDemo(UserMapper userMapper, QuestionMapper questionMapper, PasswordEncoder encoder,
                            JdbcTemplate jdbcTemplate, AccountProperties.Demo demo) {
        if (!demo.isEnabled()) {
            log.info("演示账号已禁用（kaoyan.demo.enabled=false）");
            return;
        }
        String username = demo.getUsername();
        User existing = findByUsername(userMapper, username);
        if (existing == null) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@demo.local");
            user.setPasswordHash(encoder.encode(demo.getPassword()));
            user.setIsActive(1);
            // 与「演示管理员」合并：同一个账号既展示学习数据，也能进只读后台
            user.setIsAdmin(1);
            user.setTokenVersion(1);
            userMapper.insert(user);
            existing = user;
            log.info("已创建演示账号 {}（管理员视图·只读，密码见 KAOYAN_DEMO_PASSWORD）", username);
        } else if (existing.getIsAdmin() == null || existing.getIsAdmin() == 0) {
            existing.setIsAdmin(1);
            existing.setIsActive(1);
            userMapper.updateById(existing);
            log.info("演示账号 {} 已提升为管理员（只读）", username);
        } else if (existing.getIsActive() != null && existing.getIsActive() == 0) {
            // 演示账号被误禁用时自动恢复：它是对外承诺的入口
            existing.setIsActive(1);
            userMapper.updateById(existing);
            log.info("演示账号 {} 已重新启用", username);
        }

        if (demo.isSeedData()) {
            seedDemoData(userMapper, questionMapper, jdbcTemplate, existing.getId());
        }
    }

    /**
     * 给演示账号铺一套「像真的用过一阵」的数据。
     *
     * <p>覆盖：220 条作答（四科多年份、约 72% 正确）、14 天连续打卡、4 次模拟考试
     * （不同年份与分数）、72 张处在不同盒/不同到期日的背诵卡、按题-知关联刷新的
     * 知识块进度。目的是让访客看到「用了一段时间之后长什么样」，而不是空面板。
     *
     * <p>幂等：用 app_meta 的 demo_seed_version 記账；版本没变就不重铺。
     * 版本升级时会清空 demo 自己的历史（只动 demo，不影响真实用户）。
     */
    private void seedDemoData(UserMapper userMapper, QuestionMapper questionMapper,
                              JdbcTemplate jdbcTemplate, Long demoUserId) {
        String seeded = null;
        try {
            seeded = jdbcTemplate.queryForObject(
                    "SELECT value FROM app_meta WHERE k = 'demo_seed_version'", String.class);
        } catch (Exception ignored) {
            // 没有记录 = 从没铺过
        }
        if (seeded != null && Integer.parseInt(seeded) >= DEMO_SEED_VERSION) {
            log.info("演示数据已是 v{}，跳过预置", seeded);
            return;
        }

        Long questionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question", Long.class);
        if (questionCount == null || questionCount == 0) {
            log.warn("题库为空，跳过演示数据预置（内容种子可能还没灌入）");
            return;
        }

        // 清掉旧的演示数据（含早先 v1 的 18 条）
        jdbcTemplate.update("DELETE FROM question_attempt WHERE user_id = ?", demoUserId);
        jdbcTemplate.update("DELETE FROM daily_stat WHERE user_id = ?", demoUserId);
        jdbcTemplate.update("DELETE FROM memory_card WHERE user_id = ?", demoUserId);
        jdbcTemplate.update("DELETE FROM block_progress WHERE user_id = ?", demoUserId);
        jdbcTemplate.update("DELETE FROM mock_answer WHERE session_id IN "
                + "(SELECT id FROM mock_session WHERE user_id = ?)", demoUserId);
        jdbcTemplate.update("DELETE FROM mock_session WHERE user_id = ?", demoUserId);

        // 1) 作答：随机取 220 题，按 index 决定对错与日期，保证分布稳定可复现
        List<Question> questions = questionMapper.selectList(new QueryWrapper<Question>()
                .last("ORDER BY RAND() LIMIT " + DEMO_ATTEMPTS_TARGET));
        int[] perDayQuestions = new int[DEMO_STREAK_DAYS];
        int[] perDayCorrect = new int[DEMO_STREAK_DAYS];
        int wrong = 0;
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            boolean correct = i % 100 < 72;
            int dayOffset = i % DEMO_STREAK_DAYS;
            LocalDate day = LocalDate.now().minusDays(dayOffset);
            java.sql.Timestamp answeredAt = java.sql.Timestamp.valueOf(
                    day.atTime(9 + (i % 10), (i * 7) % 60));
            perDayQuestions[dayOffset] += 1;
            if (correct) {
                perDayCorrect[dayOffset] += 1;
            } else {
                wrong++;
            }
            jdbcTemplate.update(
                    "INSERT INTO question_attempt (user_id, question_id, result, answer, duration_ms, "
                            + "wrong_count, retry_correct_count, first_wrong_at, mastered_at, answered_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    demoUserId, question.getId(), correct ? 1 : 0,
                    correct ? firstOption(question) : "B",
                    correct ? 9_000 + (i % 40) * 500 : 24_000,
                    correct ? 0 : 1,
                    correct && i % 5 == 0 ? 1 : 0,
                    correct ? null : java.sql.Timestamp.valueOf(day.atTime(20, 15)),
                    correct && i % 4 == 0 ? java.sql.Timestamp.valueOf(day.atTime(20, 20)) : null,
                    answeredAt);
        }

        // 2) 每日统计：与上面的作答分布对齐，热力图/连续打卡才有数据
        for (int d = 0; d < DEMO_STREAK_DAYS; d++) {
            jdbcTemplate.update(
                    "INSERT INTO daily_stat (user_id, stat_date, questions, correct, minutes) "
                            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                            + "questions = VALUES(questions), correct = VALUES(correct), minutes = VALUES(minutes)",
                    demoUserId, java.sql.Date.valueOf(LocalDate.now().minusDays(d)),
                    perDayQuestions[d], perDayCorrect[d], perDayQuestions[d] * 2);
        }

        // 3) 模拟考试：不同年份、不同分数，包含一次偏低分的
        List<Integer> years = jdbcTemplate.queryForList(
                "SELECT year FROM exam_year ORDER BY year DESC LIMIT " + DEMO_MOCK_SESSIONS, Integer.class);
        int[] autoScores = {62, 71, 58, 74};
        int[] selfScores = {38, 42, 30, 46};
        int mocks = 0;
        for (int i = 0; i < years.size() && i < autoScores.length; i++) {
            jdbcTemplate.update(
                    "INSERT INTO mock_session (user_id, exam_year, started_at, duration_minutes, status, "
                            + "submitted_at, auto_score, self_score, timed_out) VALUES (?, ?, ?, ?, 'submitted', ?, ?, ?, 0)",
                    demoUserId, years.get(i),
                    java.sql.Timestamp.valueOf(LocalDate.now().minusDays(3L * i + 2).atTime(14, 0)),
                    180,
                    java.sql.Timestamp.valueOf(LocalDate.now().minusDays(3L * i + 2).atTime(17, 5)),
                    autoScores[i], selfScores[i]);
            mocks++;
        }

        // 4) 背诵卡：不同盒、不同到期日（三分之一今天到期，制造"待复习"）
        List<Map<String, Object>> blocks = jdbcTemplate.queryForList(
                "SELECT block_id FROM knowledge_block ORDER BY RAND() LIMIT " + DEMO_CARDS_TARGET);
        int cards = 0;
        for (int i = 0; i < blocks.size(); i++) {
            int box = i % 5;
            int reviewCount = 1 + i % 4;
            LocalDate due = i % 3 == 0 ? LocalDate.now() : LocalDate.now().plusDays(1 + i % 7);
            try {
                jdbcTemplate.update(
                        "INSERT INTO memory_card (user_id, block_id, difficulty, next_review_date, box, "
                                + "review_count, correct_count, last_reviewed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        demoUserId, blocks.get(i).get("block_id"), 1 + box % 3,
                        java.sql.Date.valueOf(due), box, reviewCount,
                        Math.max(0, reviewCount - (i % 3)), java.sql.Timestamp.valueOf(due.minusDays(1).atTime(21, 0)));
                cards++;
            } catch (Exception duplicate) {
                // uk_card_user_block 唯一约束：重复就跳过
            }
        }

        // 5) 知识块进度：从作答反推，让掌握度面板/知识点列表有真实分布
        int blocksTouched = 0;
        try {
            blocksTouched = jdbcTemplate.update(
                    "INSERT INTO block_progress (user_id, block_id, questions_total, questions_done, questions_correct) "
                            + "SELECT ?, qk.block_id, COUNT(*), COUNT(a.id), COALESCE(SUM(a.result), 0) "
                            + "FROM question_knowledge qk "
                            + "LEFT JOIN question_attempt a ON a.question_id = qk.question_id AND a.user_id = ? "
                            + "GROUP BY qk.block_id "
                            + "ON DUPLICATE KEY UPDATE questions_total = VALUES(questions_total), "
                            + "questions_done = VALUES(questions_done), questions_correct = VALUES(questions_correct)",
                    demoUserId, demoUserId);
        } catch (Exception error) {
            log.warn("演示数据：知识块进度刷新失败（不影响其它数据）：{}", error.getMessage());
        }

        jdbcTemplate.update(
                "INSERT INTO app_meta (k, value) VALUES ('demo_seed_version', ?) "
                        + "ON DUPLICATE KEY UPDATE value = VALUES(value)",
                String.valueOf(DEMO_SEED_VERSION));

        log.info("演示数据已铺好：{} 条作答（错 {}）、{} 天统计、{} 次模考、{} 张背诵卡、{} 个知识块进度",
                questions.size(), wrong, DEMO_STREAK_DAYS, mocks, cards, blocksTouched);
    }
    private String firstOption(Question question) {
        return "choice".equals(question.getQuestionType()) ? "A" : "已完成";
    }

    private User findByUsername(UserMapper userMapper, String username) {
        return userMapper.selectOne(new QueryWrapper<User>()
                .eq("username", username).last("LIMIT 1"));
    }
}
