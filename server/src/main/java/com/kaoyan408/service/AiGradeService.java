package com.kaoyan408.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.Question;
import com.kaoyan408.mapper.QuestionSubQuestionMapper;
import com.kaoyan408.service.ai.CommandCodeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 综合题 AI 评改。
 *
 * <p>设计取舍（第一版）：
 * <ul>
 *   <li>只给「建议分」，最终分数由用户确认——408 综合题是过程题，AI 给分口径不稳，
 *       一旦明显离谱会伤掉用户对整个平台的信任；</li>
 *   <li>严格 JSON 输出，解析失败直接报错让前端退回自评，不做"猜一个分"；</li>
 *   <li>不做全自动入库：先积累「AI 分 vs 用户确认分」的对照数据，达标后再考虑自动。</li>
 * </ul>
 */
@Service
public class AiGradeService {

    private static final Logger log = LoggerFactory.getLogger(AiGradeService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 答案太短没有评的意思，也避免白烧一次调用 */
    private static final int MIN_ANSWER_LENGTH = 20;
    private static final int MAX_TOKENS = 1600;

    private final CommandCodeClient client;
    private final QuestionSubQuestionMapper subQuestionMapper;

    public AiGradeService(CommandCodeClient client, QuestionSubQuestionMapper subQuestionMapper) {
        this.client = client;
        this.subQuestionMapper = subQuestionMapper;
    }

    /** 该题能不能 AI 评改（前端据此决定是否显示入口）。 */
    public boolean available() {
        return client.isConfigured();
    }

    /**
     * 评改一道综合题。
     *
     * @param answer 学生手写/敲入的答案
     * @return {subpoints:[{index,max,awarded,comment}], total, max, confidence, rationale, model}
     */
    public Map<String, Object> grade(Question question, String answer) {
        if (!"comprehensive".equals(question.getQuestionType())) {
            throw new BusinessException(400, "只有综合题需要 AI 评改。");
        }
        if (!available()) {
            throw new BusinessException(503, "AI 评改暂未启用（服务端未配置模型密钥），请先用自评。");
        }
        String text = answer == null ? "" : answer.trim();
        if (text.length() < MIN_ANSWER_LENGTH) {
            throw new BusinessException(400, "答案太短（少于 " + MIN_ANSWER_LENGTH + " 字），无法评改。");
        }

        int full = question.getScore() == null ? 0 : question.getScore();
        List<Map<String, Object>> subs = subQuestionMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.kaoyan408.entity.QuestionSubQuestion>()
                        .eq("question_id", question.getId())
                        .orderByAsc("sub_number"));

        String raw;
        try {
            raw = client.complete(SYSTEM_PROMPT, userPrompt(question, subs, text, full), MAX_TOKENS);
        } catch (Exception error) {
            log.warn("AI 评改调用失败：{}", error.getMessage());
            throw new BusinessException(503, "AI 评改调用失败，请稍后重试或改用自评。");
        }

        return parse(raw, full, subs);
    }

    private static final String SYSTEM_PROMPT = """
            你是 408 计算机学科专业基础综合的阅卷老师。
            任务：按给分点给学生答案打分，并指出每一点是否命中。
            只输出一个 JSON 对象，不要 Markdown 代码块、不要任何解释文字。
            JSON 结构：
            {"subpoints":[{"index":1,"max":3,"awarded":2,"comment":"为什么给 2 分"}],
             "confidence":0.0,"rationale":"整体评价，一到两句"}
            要求：
            - subpoints 的 index 与题目给的子问序号一致，max 用题目给的满分；
            - awarded 必须是 0..max 的整数；
            - comment 要具体到"哪一步写对了/哪一步缺了"，不要写"回答较好"这种空话；
            - 学生没写到的给分点也要列出来，awarded 填 0；
            - confidence 是 0..1 的小数，表示你对这次评分的把握。
            """;

    private String userPrompt(Question question, List<Map<String, Object>> subs, String answer, int full) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题目】\n").append(nullToEmpty(question.getStem())).append("\n\n");
        if (!subs.isEmpty()) {
            sb.append("【给分点】\n");
            for (Map<String, Object> sub : subs) {
                sb.append(sub.get("sub_number")).append(". ")
                        .append(nullToEmpty((String) sub.get("description")))
                        .append("（").append(sub.get("score")).append(" 分）\n");
            }
            sb.append('\n');
        }
        sb.append("【参考答案】\n").append(nullToEmpty(question.getExplanation())).append("\n\n");
        sb.append("【学生答案】\n").append(answer).append("\n\n");
        sb.append("【本题满分】").append(full).append(" 分\n");
        sb.append("请输出 JSON。");
        return sb.toString();
    }

    private Map<String, Object> parse(String raw, int full, List<Map<String, Object>> subs) {
        String json = extractJson(raw);
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception error) {
            log.warn("AI 评改返回非 JSON：{}", raw == null ? "" : raw.substring(0, Math.min(200, raw.length())));
            throw new BusinessException(503, "AI 返回格式不正确，请重试或改用自评。");
        }

        List<Map<String, Object>> points = new ArrayList<>();
        int total = 0;
        JsonNode arr = root.path("subpoints");
        if (!arr.isArray() || arr.isEmpty()) {
            throw new BusinessException(503, "AI 没有给出分点评分，请重试或改用自评。");
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonNode node = arr.get(i);
            int max = node.path("max").asInt(i < subs.size() ? toInt(subs.get(i).get("score")) : 0);
            int awarded = Math.max(0, Math.min(max, node.path("awarded").asInt(0)));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", node.path("index").asInt(i + 1));
            item.put("max", max);
            item.put("awarded", awarded);
            item.put("comment", node.path("comment").asText(""));
            points.add(item);
            total += awarded;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subpoints", points);
        payload.put("total", Math.min(total, full));
        payload.put("max", full);
        payload.put("confidence", Math.max(0.0, Math.min(1.0, root.path("confidence").asDouble(0.5))));
        payload.put("rationale", root.path("rationale").asText(""));
        payload.put("model", client.modelName());
        payload.put("disclaimer", "AI 评分仅供参考，最终分数以你确认的为准。");
        return payload;
    }

    /** 容忍模型把 JSON 包在 ```json 里，或前后带寒暄。 */
    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}