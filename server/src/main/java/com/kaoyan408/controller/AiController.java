package com.kaoyan408.controller;

import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.User;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.ai.AiChatService;
import com.kaoyan408.service.ai.DiagramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 学习助手。
 *
 * <p>对话接口走 SSE 而不是普通 JSON：模型带推理，完整回答要 6 秒左右，
 * 一次性返回会让用户盯着空白等。SSE 让首字 1-2 秒就出现。
 *
 * <p>为什么用 SseEmitter 而不是返回 Flux：项目是 MVC + MyBatis，
 * 引 WebFlux 会带进整套响应式栈。SseEmitter 在 MVC 下就能用，
 * 代价是要自己管线程池（见下面的 executor）。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    /**
     * 流式输出用的线程池。
     *
     * <p>不能用 ForkJoinPool.commonPool：那里的线程被并行流共用，
     * 一条慢速 AI 流会占住公共线程，影响其它业务。
     * 这里给有限线程，超出的请求排队，避免一次涌入把上游打爆。
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ai-stream");
        t.setDaemon(true);
        return t;
    });

    private final AiChatService aiChatService;
    private final DiagramService diagramService;
    private final AuthService authService;

    public AiController(AiChatService aiChatService,
                        DiagramService diagramService,
                        AuthService authService) {
        this.aiChatService = aiChatService;
        this.diagramService = diagramService;
        this.authService = authService;
    }

    public record ChatRequest(Long conversationId, String question) {
    }

    /** 服务状态：是否已配置、游客还剩几次。 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(Authentication authentication,
                                              jakarta.servlet.http.HttpServletRequest request) {
        return Result.ok(aiChatService.status(userIdOf(authentication), clientIp(request)));
    }

    /** 会话列表；游客返回空数组。 */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(Authentication authentication) {
        return Result.ok(aiChatService.listConversations(userIdOf(authentication)));
    }

    /** 单条会话内容。 */
    @GetMapping("/conversations/{id}")
    public Result<Map<String, Object>> conversation(@PathVariable Long id,
                                                    Authentication authentication) {
        return Result.ok(aiChatService.conversation(userIdOf(authentication), id));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Map<String, Object>> deleteConversation(@PathVariable Long id,
                                                          Authentication authentication) {
        aiChatService.deleteConversation(userIdOf(authentication), id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", true);
        return Result.ok(out);
    }

    public record DiagramRequest(String question, String answerExcerpt, String knowledge) {
    }

    /**
     * 生成分步动画（一次性返回，非流式）。
     *
     * <p>和 /diagram 的区别：返回的 DSL 多了 steps，前端可逐步播放。
     * 缓存键带 anim: 前缀，与同问题的静态图解互不干扰。
     *
     * <p>同样计入游客 3 次限额：动画比静态图更贵（输出长一倍以上）。
     */
    @PostMapping("/animation")
    public Result<Map<String, Object>> animation(@RequestBody(required = false) DiagramRequest body,
                                                 Authentication authentication,
                                                 jakarta.servlet.http.HttpServletRequest request) {
        Long userId = userIdOf(authentication);
        String ip = clientIp(request);
        aiChatService.ensureQuota(userId, ip);
        return Result.ok(diagramService.generateAnimation(
                body == null ? null : body.question(),
                body == null ? null : body.answerExcerpt(),
                body == null ? null : body.knowledge()));
    }

    /**
     * 生成图解（一次性返回，非流式）。
     *
     * <p>不流式：图要拿到完整 JSON 才能画，边收边画没有意义。
     * 生成结果按问题哈希缓存，同一问题第二次几乎是秒回。
     *
     * <p>对游客同样开放，但计入同一条 3 次限额——生成一张图比一轮对话更贵。
     */
    @PostMapping("/diagram")
    public Result<Map<String, Object>> diagram(@RequestBody(required = false) DiagramRequest body,
                                               Authentication authentication,
                                               jakarta.servlet.http.HttpServletRequest request) {
        Long userId = userIdOf(authentication);
        String ip = clientIp(request);
        aiChatService.ensureQuota(userId, ip);
        return Result.ok(diagramService.generate(
                body == null ? null : body.question(),
                body == null ? null : body.answerExcerpt(),
                body == null ? null : body.knowledge()));
    }

    /**
     * 对话（流式）。
     *
     * <p>事件类型：
     * <ul>
     *   <li>{@code thinking} —— 模型开始推理，前端显示「思考中」</li>
     *   <li>{@code delta} —— 正文片段，逐字追加</li>
     *   <li>{@code done} —— 结束，带 conversationId 与 usage</li>
     *   <li>{@code error} —— 出错，带可读信息</li>
     * </ul>
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody(required = false) ChatRequest body,
                           Authentication authentication,
                           jakarta.servlet.http.HttpServletRequest request) {
        // 超时给 3 分钟：模型推理 + 长回答偶尔会超过 2 分钟，
        // 设太短会在正常回答中途被掐断。
        SseEmitter emitter = new SseEmitter(180_000L);
        Long userId = userIdOf(authentication);
        String ip = clientIp(request);

        Long conversationId = body == null ? null : body.conversationId();
        String question = body == null ? null : body.question();
        final Long finalConvId = conversationId;

        emitter.onCompletion(() -> log.debug("AI 流结束"));
        emitter.onTimeout(() -> log.warn("AI 流超时"));
        emitter.onError(e -> log.warn("AI 流异常: {}", e.getMessage()));

        executor.submit(() -> {
            try {
                aiChatService.chat(
                        userId, ip, finalConvId, question,
                        () -> sendQuietly(emitter, "thinking", Map.of("status", "thinking")),
                        chunk -> sendQuietly(emitter, "delta", Map.of("text", chunk)),
                        usage -> {
                            Map<String, Object> done = new LinkedHashMap<>();
                            done.put("conversationId", finalConvId);
                            done.put("promptTokens", usage.promptTokens());
                            done.put("completionTokens", usage.completionTokens());
                            // 游客拿到会话 id 也存不下来，前端据此提示登录
                            done.put("guest", userId == null);
                            sendQuietly(emitter, "done", done);
                            emitter.complete();
                        });
            } catch (BusinessException e) {
                sendQuietly(emitter, "error", Map.of("code", e.getCode(), "message", e.getMessage()));
                emitter.complete();
            } catch (Exception e) {
                log.error("AI 对话失败", e);
                sendQuietly(emitter, "error", Map.of("code", 500, "message", "AI 服务暂时不可用，请稍后重试。"));
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 发送一个 SSE 事件；客户端已断开时不抛异常。
     *
     * <p>不这样做的话，用户中途关页面会在服务端抛 IOException，
     * 每次都要打一条错误日志，噪音很大。
     */
    private static void sendQuietly(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) {
            // 客户端断开，正常结束
        }
    }

    private Long userIdOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        User user = authService.findByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }

    /** 取真实客户端 IP；经过 nginx 代理后 remoteAddr 是容器地址，必须看转发头。 */
    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}