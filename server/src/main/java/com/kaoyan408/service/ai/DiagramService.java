package com.kaoyan408.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.AiDiagram;
import com.kaoyan408.mapper.AiDiagramMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 生成图解。
 *
 * <p>核心是**不信任模型输出**：模型返回的 JSON 先在这里过一遍结构与取值校验，
 * 不合格就丢弃并如实报错，绝不把原始字符串透给前端当代码执行。
 * 前端拿到的一定是「字段齐全、坐标在画布内、节点数受限」的干净结构。
 *
 * <p>校验放在服务端而不是只在前端：前端校验是体验优化，
 * 服务端校验才是安全边界（还能挡住乱写的内容进缓存）。
 */
@Service
public class DiagramService {

    private static final Logger log = LoggerFactory.getLogger(DiagramService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 上限：超过就判为不合格，避免生成过于复杂、渲染后糊成一团的图。 */
    private static final int MAX_NODES = 12;
    private static final int MAX_EDGES = 16;
    private static final int MAX_NOTES = 2;
    private static final int MAX_TITLE_LEN = 24;
    private static final int MAX_LABEL_LEN = 24;

    private static final Set<String> SHAPES = Set.of("box", "circle", "diamond", "text");
    private static final Set<String> COLORS = Set.of("default", "accent", "warn", "danger", "muted");
    private static final Set<String> EDGE_STYLES = Set.of("solid", "dashed", "arrow");

    private final CommandCodeClient client;
    private final AiDiagramMapper diagramMapper;

    public DiagramService(CommandCodeClient client, AiDiagramMapper diagramMapper) {
        this.client = client;
        this.diagramMapper = diagramMapper;
    }

    /**
     * 为一个问题生成图解。
     *
     * @param question      用户的问题
     * @param answerExcerpt 已生成的文字回答片段，帮助模型判断该画什么
     * @param knowledge     相关知识点标题，可为空
     */
    public Map<String, Object> generate(String question, String answerExcerpt, String knowledge) {
        if (!client.isConfigured()) {
            throw new BusinessException(503, "AI 服务未配置，无法生成图解。");
        }
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new BusinessException(400, "缺少问题内容。");
        }

        String key = hash(q + "|" + (knowledge == null ? "" : knowledge));

        // 先查缓存：同一问题图解内容不变，没必要重复花钱
        AiDiagram cached = diagramMapper.selectOne(
                new QueryWrapper<AiDiagram>().eq("question_hash", key).last("LIMIT 1"));
        if (cached != null) {
            cached.setHitCount((cached.getHitCount() == null ? 0 : cached.getHitCount()) + 1);
            diagramMapper.updateById(cached);
            Map<String, Object> out = new HashMap<>();
            out.put("dsl", readTree(cached.getDslJson()));
            out.put("cached", true);
            return out;
        }

        // 图解与正文共用 max_tokens 时容易被推理吃掉，单独放宽到 3000
        String raw = client.complete(
                DiagramPrompt.systemPrompt(knowledge),
                DiagramPrompt.userPrompt(q, answerExcerpt),
                3000);

        JsonNode validated = validate(extractJson(raw));
        if (validated == null) {
            log.warn("图解生成不合格，已丢弃。原始输出前 300 字: {}", truncate(raw, 300));
            throw new BusinessException(502, "这次没画出合格的图解，换个问法或再试一次。");
        }

        AiDiagram row = new AiDiagram();
        row.setQuestionHash(key);
        row.setQuestion(truncate(q, 500));
        row.setDslJson(validated.toString());
        row.setModel(client.modelName());
        row.setHitCount(0);
        row.setCreatedAt(LocalDateTime.now());
        try {
            diagramMapper.insert(row);
        } catch (Exception e) {
            // 并发下可能撞唯一键，说明别人已经写进去了，直接忽略
            log.debug("图解入库冲突，忽略: {}", e.getMessage());
        }

        Map<String, Object> out = new HashMap<>();
        out.put("dsl", validated);
        out.put("cached", false);
        return out;
    }

    /* ---------- 解析与校验 ---------- */

    /**
     * 从模型输出里抠出 JSON。
     *
     * <p>模型经常无视「只输出 JSON」的要求，包一层 ```json 围栏或在前后加解释。
     * 这里按代码块优先、再按首尾大括号的顺序尝试，而不是一失败就报错。
     */
    private JsonNode extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();

        // 1. ```json ... ``` 围栏
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start + 1);
            if (start > 0 && end > start) {
                String inner = text.substring(start + 1, end).trim();
                JsonNode n = tryParse(inner);
                if (n != null) return n;
            }
        }

        // 2. 首个 { 到最后一个 }
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first >= 0 && last > first) {
            JsonNode n = tryParse(text.substring(first, last + 1));
            if (n != null) return n;
        }

        return tryParse(text);
    }

    private JsonNode tryParse(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 结构校验并规整。
     *
     * <p>返回 null 表示不合格。合格时返回一个新对象，只保留白名单字段——
     * 模型多给的字段一律丢弃，前端不必防着未知属性。
     */
    private JsonNode validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }

        int width = clamp(root.path("width").asInt(720), 480, 900);
        int height = clamp(root.path("height").asInt(420), 320, 700);

        ObjectNode out = JSON.createObjectNode();
        out.put("title", clean(root.path("title").asText(""), MAX_TITLE_LEN));
        out.put("width", width);
        out.put("height", height);

        // 节点
        JsonNode nodesIn = root.path("nodes");
        if (!nodesIn.isArray() || nodesIn.isEmpty()) {
            return null;
        }
        ArrayNode nodes = out.putArray("nodes");
        Set<String> ids = new java.util.HashSet<>();
        int n = 0;
        for (JsonNode node : nodesIn) {
            if (n++ >= MAX_NODES) {
                break;
            }
            String id = clean(node.path("id").asText(""), 24);
            String label = clean(node.path("label").asText(""), MAX_LABEL_LEN);
            if (id.isEmpty() || label.isEmpty() || !ids.add(id)) {
                continue;
            }
            // 坐标必须落在画布内，否则节点会画到看不见的地方
            int x = clamp(node.path("x").asInt(width / 2), 30, width - 30);
            int y = clamp(node.path("y").asInt(height / 2), 24, height - 24);
            String shape = enumOr(node.path("shape").asText(""), SHAPES, "box");
            String color = enumOr(node.path("color").asText(""), COLORS, "default");

            ObjectNode n2 = nodes.addObject();
            n2.put("id", id);
            n2.put("label", label);
            n2.put("shape", shape);
            n2.put("x", x);
            n2.put("y", y);
            n2.put("color", color);
        }
        if (nodes.isEmpty()) {
            return null;
        }

        // 边：两端必须存在，否则丢弃
        ArrayNode edges = out.putArray("edges");
        JsonNode edgesIn = root.path("edges");
        if (edgesIn.isArray()) {
            int e = 0;
            for (JsonNode edge : edgesIn) {
                if (e++ >= MAX_EDGES) {
                    break;
                }
                String from = edge.path("from").asText("");
                String to = edge.path("to").asText("");
                if (!ids.contains(from) || !ids.contains(to) || from.equals(to)) {
                    continue;
                }
                ObjectNode o = edges.addObject();
                o.put("from", from);
                o.put("to", to);
                o.put("label", clean(edge.path("label").asText(""), 16));
                o.put("style", enumOr(edge.path("style").asText(""), EDGE_STYLES, "arrow"));
            }
        }

        // 备注
        ArrayNode notes = out.putArray("notes");
        JsonNode notesIn = root.path("notes");
        if (notesIn.isArray()) {
            int k = 0;
            for (JsonNode note : notesIn) {
                if (k++ >= MAX_NOTES) {
                    break;
                }
                String text = clean(note.path("text").asText(""), 60);
                if (text.isEmpty()) {
                    continue;
                }
                ObjectNode o = notes.addObject();
                o.put("text", text);
                o.put("x", clamp(note.path("x").asInt(32), 12, width - 12));
                o.put("y", clamp(note.path("y").asInt(height - 40), 16, height - 16));
            }
        }

        return out;
    }

    private static String enumOr(String value, Set<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 去控制字符、压缩空白、截断。 */
    private static String clean(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").replaceAll("[ \\t]+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private JsonNode readTree(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "…");
    }

    /** 供健康检查用：缓存里已有多少张图。 */
    public long cachedCount() {
        Long n = diagramMapper.selectCount(new QueryWrapper<>());
        return n == null ? 0 : n;
    }
}