package com.kaoyan408;

import com.kaoyan408.core.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    @DisplayName("队列：分页返回本页条数，total 是筛选后的总数，并带年份分组")
    void queueSupportsPagination() throws Exception {
        mockMvc.perform(get("/api/v1/practice/queue")
                        .param("year", "2024")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(47))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.years.length()").value(1))
                .andExpect(jsonPath("$.data.years[0].year").value(2024));
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
