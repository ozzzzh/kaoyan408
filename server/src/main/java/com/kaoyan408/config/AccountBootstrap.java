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
    private static final int DEMO_ATTEMPTS = 18;
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
            user.setIsAdmin(0);
            user.setTokenVersion(1);
            userMapper.insert(user);
            existing = user;
            log.info("已创建演示账号 {}（密码见 KAOYAN_DEMO_PASSWORD）", username);
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
     * 给演示账号铺一点历史数据：做题记录（含几道错题）、掌握度、每日统计、背诵卡片。
     *
     * <p>只在「该账号完全没有作答记录」时执行，所以重启不会越积越多。
     */
    private void seedDemoData(UserMapper userMapper, QuestionMapper questionMapper,
                              JdbcTemplate jdbcTemplate, Long demoUserId) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_attempt WHERE user_id = ?", Integer.class, demoUserId);
        if (existing != null && existing > 0) {
            log.info("演示账号已有 {} 条作答记录，跳过数据预置", existing);
            return;
        }

        List<Question> questions = questionMapper.selectList(new QueryWrapper<Question>()
                .orderByAsc("exam_id", "exam_number").last("LIMIT " + DEMO_ATTEMPTS));
        if (questions.isEmpty()) {
            log.warn("题库为空，跳过演示数据预置（内容种子可能还没灌入）");
            return;
        }

        Map<String, Integer> perBlock = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            // 前 DEMO_WRONG 道记成答错，用来触发错题本与薄弱点
            boolean correct = i >= DEMO_WRONG;
            jdbcTemplate.update(
                    "INSERT INTO question_attempt (user_id, question_id, result, answer, duration_ms, "
                            + "wrong_count, retry_correct_count, first_wrong_at, mastered_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    demoUserId, question.getId(), correct ? 1 : 0,
                    correct ? firstOption(question) : "B",
                    12_000,
                    correct ? 0 : 1,
                    0,
                    correct ? null : java.sql.Timestamp.valueOf(LocalDate.now().minusDays(2).atTime(20, 15)),
                    null);
        }

        // 每日统计：近 3 天各有一点练习量，热力图不至于是空的
        for (int d = 0; d < 3; d++) {
            int questionsToday = Math.max(1, DEMO_ATTEMPTS / 3);
            jdbcTemplate.update(
                    "INSERT INTO daily_stat (user_id, stat_date, questions, correct, minutes) VALUES (?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE questions = VALUES(questions), correct = VALUES(correct), "
                            + "minutes = VALUES(minutes)",
                    demoUserId, java.sql.Date.valueOf(LocalDate.now().minusDays(d)),
                    questionsToday, Math.max(0, questionsToday - DEMO_WRONG / 3), questionsToday * 2);
        }

        // 背诵卡片：几张今天到期的卡，演示间隔重复入口。
        // 表结构只有 block_id / box / 复习时间这些字段，卡片正反面是按 block 现算的。
        List<Map<String, Object>> blocks = jdbcTemplate.queryForList(
                "SELECT block_id FROM knowledge_block ORDER BY block_id LIMIT " + DEMO_CARDS);
        int cards = 0;
        for (Map<String, Object> block : blocks) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO memory_card (user_id, block_id, difficulty, next_review_date, box, "
                                + "review_count, correct_count) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        demoUserId, block.get("block_id"), 2,
                        java.sql.Date.valueOf(LocalDate.now()), 1, 0, 0);
                cards++;
            } catch (Exception duplicate) {
                // uk_card_user_block 唯一约束：重复就跳过
            }
        }
        log.info("演示账号已预置 {} 条作答、3 天统计、{} 张背诵卡", questions.size(), cards);
    }

    private String firstOption(Question question) {
        return "choice".equals(question.getQuestionType()) ? "A" : "已完成";
    }

    private User findByUsername(UserMapper userMapper, String username) {
        return userMapper.selectOne(new QueryWrapper<User>()
                .eq("username", username).last("LIMIT 1"));
    }
}
