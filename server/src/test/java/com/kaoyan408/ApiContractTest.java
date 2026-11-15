package com.kaoyan408;

import com.kaoyan408.core.JwtUtil;
import com.kaoyan408.entity.User;
import com.kaoyan408.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 接口层回归测试。
 *
 * <p>这里刻意只放**不变量**级别的断言，而不是把 scripts/verify-*.mjs 那套端到端用例
 * 再抄一遍：那些脚本覆盖业务流程（搜索相关性、错题闭环、SRS 盒号、模拟考评分），
 * 需要真实启动服务、耗时几分钟；本文件负责的是「几条关键约束不能被改坏」，
 * 单个用例毫秒级，可以在每次改动时随手指跑。
 *
 * <p>运行需要可用的 MySQL（Flyway 会自动迁移，题库已存在时不会重复灌种子）：
 * <pre>
 * mvn -B test -DDB_URL='jdbc:mysql://127.0.0.1:33061/kaoyan_db?...' -DDB_USER=kaoyan -DDB_PASSWORD=...
 * </pre>
 * 未设置 DB_URL 时默认连 127.0.0.1:3306/kaoyan_db（README 里的本地开发约定）。
 */
@SpringBootTest(properties = {
        // 测试不落真邮件，也不需要真实 AI Key
        "kaoyan.mail.mode=mock",
        "kaoyan.seed.enabled=true",
        "jwt.secret=test-only-secret-at-least-32-bytes-long-0123456789"
})
@AutoConfigureMockMvc
class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("健康检查会真的探库，并带出题库条数")
    void healthChecksDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.db").value("up"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.questions").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("匿名取题不下发答案与解析（防作弊底线）")
    void anonymousQuestionHidesAnswer() throws Exception {
        mockMvc.perform(get("/api/v1/questions/exam-2024-1"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.options.length()").value(4))
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andExpect(jsonPath("$.data.explanation").doesNotExist());
    }

    @Test
    @DisplayName("背题模式必须登录：匿名请求被拒")
    void memorizeRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/questions/exam-2024-1").param("memorize", "true"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("整卷接口不下发答案，且选项按题分组不会串题")
    void examDetailHasNoAnswersAndCorrectOptions() throws Exception {
        mockMvc.perform(get("/api/v1/exams/2024"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.questionCount").value(47))
                .andExpect(jsonPath("$.data.questions.length()").value(47))
                .andExpect(jsonPath("$.data.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist());
    }

    @Test
    @DisplayName("队列：按年份加载 + 分页；total=全范围总数，yearTotal=本年份题数，years 覆盖全部年份")
    void queueSupportsYearScopedPaging() throws Exception {
        mockMvc.perform(get("/api/v1/practice/queue")
                        .param("year", "2024")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(jsonPath("$.code").value(0))
                // 选中年份的题量
                .andExpect(jsonPath("$.data.yearTotal").value(47))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                // 全范围总数（846）与年份汇总（18 卷）不受 year 过滤影响，供导航使用
                .andExpect(jsonPath("$.data.total").value(846))
                .andExpect(jsonPath("$.data.years.length()").value(18));
    }

    @Test
    @DisplayName("队列：latest=true 只加载最新一卷（不再一次下发 846 条）")
    void queueLatestLoadsSingleYear() throws Exception {
        mockMvc.perform(get("/api/v1/practice/queue").param("latest", "true"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.yearTotal").value(47))
                .andExpect(jsonPath("$.data.items.length()").value(47));
    }

    @Test
    @DisplayName("伪造 X-Forwarded-For 换不到新的限流桶（同一 X-Real-IP 共用一个桶）")
    void spoofedForwardedForDoesNotResetRateLimit() throws Exception {
        String fakeIp = "198.51.100.77";
        int lastCode = 0;
        for (int i = 1; i <= 11; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/send-mail-code")
                            .header("X-Real-IP", fakeIp)
                            // 每次都换一个 XFF：改造前这会被当成分属不同 IP
                            .header("X-Forwarded-For", "10.1.1." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"probe" + i + "@example.com\"}"))
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            lastCode = body.contains("\"code\":0") ? 0 : 429;
        }
        assertThat(lastCode)
                .as("第 11 次请求应被同一 IP 的时窗限流挡住；仍返回 0 说明 XFF 又被当成了独立 IP")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("登录失败次数达到上限后锁定（REQUIRES_NEW 计数没被业务异常回滚）")
    void loginLocksAfterTooManyFailures() throws Exception {
        String username = "lockprobe_" + System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    @DisplayName("非法 JSON 返回 400，而不是落到兜底变 500")
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{username:oops}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("JWT 密钥等于仓库默认值或过短时，构造即失败（不给出「能启动但可被伪造」的状态）")
    void weakJwtSecretIsRejected() {
        assertThatThrownBy(() -> new JwtUtil("kaoyan408-local-dev-secret-change-before-launch", 3600, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认值");
        assertThatThrownBy(() -> new JwtUtil("too-short", 3600, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("太短");
    }

    @Test
    @DisplayName("应急开关放行默认/过短密钥，但要求显式开启（默认仍是拒绝）")
    void insecureJwtSecretRequiresExplicitOptIn() {
        // 显式开启后允许启动：运维在线上起不来时改一个环境变量即可恢复，不必改代码重新构建
        JwtUtil loose = new JwtUtil("kaoyan408-local-dev-secret-change-before-launch", 3600, true);
        String token = loose.sign("u", "u@example.com", 1);
        assertThat(loose.subject(token)).isEqualTo("u");

        // 过短密钥在放行模式下做 SHA-256 派生，至少能签发与校验
        JwtUtil derived = new JwtUtil("short", 3600, true);
        assertThat(derived.subject(derived.sign("v", "v@example.com", 1))).isEqualTo("v");

        // 但默认行为不变：不开开关就拒绝
        assertThatThrownBy(() -> new JwtUtil("kaoyan408-local-dev-secret-change-before-launch", 3600, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("调高 token_version 后重新登录，新 token 必须立刻可用（防止账号状态缓存把新 ver 挡掉）")
    void freshTokenWorksRightAfterVersionBump() throws Exception {
        String username = "verprobe_" + System.currentTimeMillis();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("test123456"));
        user.setIsActive(1);
        user.setIsAdmin(0);
        user.setTokenVersion(1);
        userMapper.insert(user);

        try {
            String token1 = loginAndGetToken(username);
            // 模拟「管理员把某人踢下线 / 禁用后恢复」
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User>()
                    .eq("username", username).setSql("token_version = token_version + 1"));

            // 紧接着重新登录：旧实现会因缓存里仍是 ver=1 而让新 token(ver=2) 被判无效
            String token2 = loginAndGetToken(username);
            mockMvc.perform(get("/api/v1/user/current").header("Authorization", "Bearer " + token2))
                    .andExpect(jsonPath("$.code").value(0));

            // 旧 token 理论上应失效；缓存可能在 60 秒内仍放行旧 ver，这里只断言新 token 可用，
            // 不断言旧 token 立刻失效（那是缓存 TTL 的有意取舍）。
            assertThat(token2).isNotEqualTo(token1);
        } finally {
            userMapper.deleteById(user.getId());
        }
    }

    private String loginAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"test123456\"}"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        var node = JSON.readTree(body);
        assertThat(node.path("code").asInt()).as("登录应成功: " + body).isEqualTo(0);
        return node.path("data").path("token").asText();
    }

    @Test
    @DisplayName("令牌版本参与校验：版本不一致的 token 解析后版本号也不同")
    void tokenVersionIsCarriedInToken() {
        JwtUtil jwtUtil = new JwtUtil("test-only-secret-at-least-32-bytes-long-0123456789", 3600, false);
        String v1 = jwtUtil.sign("someone", "a@b.c", 1);
        String v2 = jwtUtil.sign("someone", "a@b.c", 2);
        assertThat(jwtUtil.tokenVersion(jwtUtil.parse(v1))).isEqualTo(1);
        assertThat(jwtUtil.tokenVersion(jwtUtil.parse(v2))).isEqualTo(2);
        assertThat(jwtUtil.subject(v1)).isEqualTo("someone");
    }
}
