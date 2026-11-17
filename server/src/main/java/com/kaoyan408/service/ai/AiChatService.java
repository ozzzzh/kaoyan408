package com.kaoyan408.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.AiConversation;
import com.kaoyan408.entity.AiMessage;
import com.kaoyan408.mapper.AiConversationMapper;
import com.kaoyan408.mapper.AiMessageMapper;
import com.kaoyan408.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 学习助手的业务编排：会话管理、上下文拼装、流式转发、用量限制。
 *
 * <p>三个刻意的设计决定：
 *
 * <p><b>一、游客不入库。</b>游客没有账号，会话写到库里就是无主数据，
 * 既无法归属也无法清理。游客走内存态（同一个请求内用完即弃），
 * 与模拟考试的处理一致。
 *
 * <p><b>二、游客限 3 次。</b>AI 调用有真实成本，不限制等于开放给全网白用。
 * 计数按 IP 记在 auth_rate_limit 上，复用已有表，不额外建。
 *
 * <p><b>三、历史只带最近 10 轮。</b>长会话全量回传会让每次请求都变贵变慢，
 * 而早期的对话对当前问题的价值很低。10 轮足够覆盖「刚才说到哪」。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** 回传给模型的历史消息条数（user + assistant 各算一条）。 */
    private static final int HISTORY_LIMIT = 20;

    /** 会话列表最多返回多少条。 */
    private static final int CONVERSATION_LIST_LIMIT = 50;

    /** 游客免费试用次数。 */
    static final int GUEST_TRIAL_LIMIT = 3;

    /** 前端匿名浏览器 id 的格式白名单（UUID 或 16 进制串都落在这个范围里）。 */
    private static final java.util.regex.Pattern GUEST_ID_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CommandCodeClient client;
    private final AiPromptBuilder promptBuilder;
    private final AiContextBuilder contextBuilder;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final RateLimitService rateLimitService;

    public AiChatService(CommandCodeClient client,
                         AiPromptBuilder promptBuilder,
                         AiContextBuilder contextBuilder,
                         AiConversationMapper conversationMapper,
                         AiMessageMapper messageMapper,
                         RateLimitService rateLimitService) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.contextBuilder = contextBuilder;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.rateLimitService = rateLimitService;
    }

    /** 数据源状态：前端据此决定是否禁用输入框。 */
    public Map<String, Object> status(Long userId, String clientIp, String guestId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", client.isConfigured());
        out.put("model", client.modelName());
        if (userId == null) {
            int used = guestUsed(quotaKey(clientIp, guestId));
            out.put("guest", true);
            out.put("guestUsed", used);
            out.put("guestRemaining", Math.max(0, GUEST_TRIAL_LIMIT - used));
        } else {
            out.put("guest", false);
        }
        return out;
    }

    public List<Map<String, Object>> listConversations(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<AiConversation> rows = conversationMapper.selectList(
                new QueryWrapper<AiConversation>()
                        .eq("user_id", userId)
                        .orderByDesc("updated_at")
                        .last("LIMIT " + CONVERSATION_LIST_LIMIT));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (AiConversation c : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("updatedAt", c.getUpdatedAt());
            out.add(m);
        }
        return out;
    }

    /** 单条会话的全部消息（按时间正序）。 */
    public Map<String, Object> conversation(Long userId, Long conversationId) {
        AiConversation conv = requireOwned(userId, conversationId);
        List<AiMessage> rows = messageMapper.selectList(
                new QueryWrapper<AiMessage>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("id"));
        List<Map<String, Object>> msgs = new ArrayList<>(rows.size());
        for (AiMessage m : rows) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("role", m.getRole());
            one.put("content", m.getContent());
            one.put("createdAt", m.getCreatedAt());
            msgs.add(one);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", conv.getId());
        out.put("title", conv.getTitle());
        out.put("messages", msgs);
        return out;
    }

    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        // 消息表有 ON DELETE CASCADE，删会话即连带删消息
        conversationMapper.deleteById(conversationId);
    }


    /**
     * 检查并扣减游客额度。登录用户不限次，直接返回。
     *
     * <p>抽成公开方法是因为图解生成也要走同一份额度：
     * 生成一张图比一轮对话更贵，游客不该靠换接口绕开限制。
     *
     * <p>在调用模型**之前**扣：模型调用可能失败或超时，
     * 若等成功再扣，失败重试就能无限刷。
     */
    public void ensureQuota(Long userId, String clientIp, String guestId) {
        if (userId != null) {
            return;
        }
        String key = quotaKey(clientIp, guestId);
        int used = guestUsed(key);
        if (used >= GUEST_TRIAL_LIMIT) {
            throw new BusinessException(429,
                    "游客试用次数已用完（共 " + GUEST_TRIAL_LIMIT
                            + " 次）。登录后可继续使用，并能结合你的做题数据。");
        }
        consumeGuestQuota(key);
    }

    /**
     * 游客额度的计数键。
     *
     * <p>优先用前端生成的匿名浏览器 id：站点在隧道后面，nginx 看到的 remote_addr
     * 是隧道对端而不是访客，按 IP 记账会让所有游客共用一个 3 次的桶
     * （谁先用完，新访客一进来就显示「剩余 0 次」）。
     *
     * <p>id 格式做了白名单校验，拿不到或格式不对才退回 IP，保证不会因为
     * 前端不传头就把额度算到同一个空串上。
     */
    private static String quotaKey(String clientIp, String guestId) {
        String id = guestId == null ? "" : guestId.trim();
        if (GUEST_ID_PATTERN.matcher(id).matches()) {
            return "anon:" + id;
        }
        return "ip:" + normalizeIp(clientIp);
    }

    /**
     * 发起一轮对话。
     *
     * <p>不会在方法上加 @Transactional：这里要长时间持有 HTTP 连接做流式输出，
     * 同一个事务会开一整段对话那么久，把连接池拖干。
     * 写库动作拆到每次回调里各自提交（见 persistAssistant）。
     *
     * @param onThinking 模型开始推理时回调，用于前端显示「思考中」
     * @param onDelta    正文分片回调
     * @param onDone     结束回调，带用量
     */
    public void chat(Long userId, String clientIp, String guestId, Long conversationId,
                     String question,
                     Runnable onThinking,
                     Consumer<String> onDelta,
                     Consumer<CommandCodeClient.Usage> onDone,
                     CommandCodeClient.Override override) {

        boolean ownKey = override != null && override.hasKey();
        if (!client.isConfigured() && !ownKey) {
            throw new BusinessException(503, "AI 服务未配置，请联系管理员。");
        }
        if (override != null && !client.isAllowedBase(override.baseUrl())) {
            // 任意 baseUrl 等于把服务器当 SSRF 跳板：只放行已知的 OpenAI 兼容服务
            throw new BusinessException(400, "自定义接口地址不在允许范围内，请改用白名单内的服务商。");
        }
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new BusinessException(400, "请输入问题。");
        }
        if (q.length() > 2000) {
            throw new BusinessException(400, "问题太长了，请精简到 2000 字以内。");
        }

        boolean guest = userId == null;
        ensureQuota(userId, clientIp, guestId);

        // 会话：游客不落库，每次都是新会话
        AiConversation conv = null;
        if (!guest) {
            conv = conversationId == null
                    ? createConversation(userId, q)
                    : requireOwned(userId, conversationId);
            persistMessage(conv.getId(), "user", q, null, null);
        }

        String systemPrompt = promptBuilder.build(contextBuilder.build(userId));

        // 历史只带最近若干轮：早期对话对当前问题价值低，
        // 全量回传会让每次请求都变贵、变慢。
        // 注意此时本次提问刚写进库，historyOf 会把最后一条也就是它自己带出来，
        // 所以这里取完再丢掉末尾那条，避免同一句话发两遍。
        List<Map<String, String>> history = conv == null ? List.of() : historyOf(conv.getId());
        if (!history.isEmpty()) {
            Map<String, String> last = history.get(history.size() - 1);
            if ("user".equals(last.get("role")) && q.equals(last.get("content"))) {
                history = history.subList(0, history.size() - 1);
            }
        }

        StringBuilder answer = new StringBuilder();
        CommandCodeClient.Usage usage;
        try {
            usage = client.stream(
                    new CommandCodeClient.ChatInput(systemPrompt, history, q),
                    onThinking,
                    chunk -> {
                        answer.append(chunk);
                        onDelta.accept(chunk);
                    },
                    override);
        } catch (RuntimeException e) {
            // 失败时把已生成的部分存下来，用户不会白等一场
            if (conv != null && !answer.isEmpty()) {
                persistMessage(conv.getId(), "assistant", answer.toString(), null, null);
            }
            throw e;
        }

        if (answer.isEmpty()) {
            // 上游一个分片都没给、又没抛异常时，原来会当作成功：
            // 会话里只剩用户那句提问，前端留下一个空气泡，日志里一行线索都没有。
            // 这里改成显式失败，错误信息会通过 SSE 的 error 事件显示给用户，
            // 同时把模型与用量写进日志，方便定位是额度、密钥还是模型名的问题。
            log.warn("AI 上游零内容：model={} 用量={}/{}",
                    client.modelName(), usage.promptTokens(), usage.completionTokens());
            throw new BusinessException(502,
                    "AI 这次没有返回内容（上游可能报错或额度不足），请重试；持续出现请联系管理员。");
        }

        if (conv != null) {
            persistMessage(conv.getId(), "assistant", answer.toString(),
                    usage.promptTokens(), usage.completionTokens());
            touch(conv);
        }
        onDone.accept(usage);

        if (log.isDebugEnabled()) {
            log.debug("AI 回答完成 guest={} 长度={} 用量={}/{}",
                    guest, answer.length(), usage.promptTokens(), usage.completionTokens());
        }
    }

    /* ---------- 内部 ---------- */

    private AiConversation requireOwned(Long userId, Long conversationId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录。");
        }
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !userId.equals(conv.getUserId())) {
            throw new BusinessException(404, "会话不存在。");
        }
        return conv;
    }

    @Transactional
    public AiConversation createConversation(Long userId, String firstQuestion) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle(deriveTitle(firstQuestion));
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    @Transactional
    public void persistMessage(Long conversationId, String role, String content,
                               Integer promptTokens, Integer completionTokens) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setPromptTokens(promptTokens);
        msg.setCompletionTokens(completionTokens);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    @Transactional
    public void touch(AiConversation conv) {
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    /** 取最近若干条消息转成模型要的 role/content 结构。 */
    private List<Map<String, String>> historyOf(Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        List<AiMessage> rows = messageMapper.selectList(
                new QueryWrapper<AiMessage>()
                        .eq("conversation_id", conversationId)
                        .orderByDesc("id")
                        .last("LIMIT " + HISTORY_LIMIT));
        // 查出来是倒序，回传时要正序
        List<Map<String, String>> out = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            AiMessage m = rows.get(i);
            out.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
        return out;
    }

    private static String deriveTitle(String question) {
        String t = question.replaceAll("\\s+", " ").trim();
        if (t.length() > 24) {
            t = t.substring(0, 24) + "…";
        }
        return t.isEmpty() ? "新对话" : t;
    }

    /* ---------- 游客限额 ---------- */

    private static final String GUEST_SCOPE = "ai_guest";
    private static final String GUEST_WINDOW = "all";

    private int guestUsed(String clientIp) {
        return rateLimitService.current(GUEST_SCOPE, normalizeIp(clientIp), GUEST_WINDOW);
    }

    /**
     * 扣一次游客额度。
     *
     * <p>刻意在调用模型之前扣：模型调用可能失败或超时，
     * 若等成功再扣，失败重试就能无限刷，限额形同虚设。
     */
    private void consumeGuestQuota(String clientIp) {
        rateLimitService.increment(GUEST_SCOPE, normalizeIp(clientIp), GUEST_WINDOW);
    }

    private static String normalizeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip.trim();
    }
}