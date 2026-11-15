package com.kaoyan408.service;

import com.kaoyan408.mapper.SearchMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全文搜索。
 *
 * <p>规模判断：知识点 147 + 知识块 1634 + 题目 846，合计约 2600 条文档。
 * 这个量级用 MySQL LIKE + 相关性排序即可，无需引入 Lucene / Elasticsearch。
 *
 * <p>相关性排序（分数从高到低）：标题完全相等 &gt; 标题前缀 &gt; 标题包含 &gt; 正文包含。
 * 纯 LIKE 无法给出这种分级，因此在 SQL 里用 CASE WHEN 计算 score。
 */
@Service
public class SearchService {

    /** 每类结果返回上限，避免一次下发过多。 */
    private static final int POINT_LIMIT = 20;
    private static final int QUESTION_LIMIT = 40;
    private static final int BLOCK_LIMIT = 20;

    private final SearchMapper searchMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public SearchService(SearchMapper searchMapper,
                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.searchMapper = searchMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 综合搜索：返回知识点 / 题目 / 正文片段三组结果。
     * query 为空时返回空结果而不是全量。
     */
    public Map<String, Object> search(String query) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String keyword = query == null ? "" : query.trim();
        payload.put("query", keyword);

        if (keyword.isEmpty()) {
            payload.put("total", 0);
            payload.put("points", List.of());
            payload.put("questions", List.of());
            payload.put("blocks", List.of());
            return payload;
        }

        // 题号查询：形如「2026 3」「2026年第3题」「exam-2026-3」时按年份+题号直接定位
        Integer[] yearAndNumber = parseYearAndNumber(keyword);

        List<Map<String, Object>> points = searchMapper.searchPoints(keyword, POINT_LIMIT);
        List<Map<String, Object>> questions = yearAndNumber != null
                ? searchMapper.questionByYearAndNumber(yearAndNumber[0], yearAndNumber[1])
                : searchMapper.searchQuestions(keyword, QUESTION_LIMIT);
        List<Map<String, Object>> blocks = searchMapper.searchBlocks(keyword, BLOCK_LIMIT);

        for (Map<String, Object> point : points) {
            point.put("kind", "point");
        }
        for (Map<String, Object> question : questions) {
            question.put("kind", "question");
        }
        for (Map<String, Object> block : blocks) {
            block.put("kind", "block");
            block.put("snippet", snippet(String.valueOf(block.get("text")), keyword));
            block.remove("text");
        }

        payload.put("total", points.size() + questions.size() + blocks.size());
        payload.put("points", points);
        payload.put("questions", questions);
        payload.put("blocks", blocks);
        return payload;
    }

    /**
     * 解析「年份 + 题号」形式的查询。
     * 支持：2026 3 / 2026年第3题 / 2026-3 / exam-2026-3。
     * 解析不出返回 null，走普通关键词搜索。
     */
    private Integer[] parseYearAndNumber(String keyword) {
        String normalized = keyword.replaceAll("\\s+", " ").trim();

        // exam-2026-3
        var examRef = java.util.regex.Pattern
                .compile("^exam-(\\d{4})-(\\d{1,2})$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (examRef.matches()) {
            return new Integer[]{Integer.parseInt(examRef.group(1)), Integer.parseInt(examRef.group(2))};
        }

        // 2026年第3题 / 2026 第 3 题
        var chinese = java.util.regex.Pattern
                .compile("^(\\d{4})\\s*年?\\s*第?\\s*(\\d{1,2})\\s*题?$")
                .matcher(normalized);
        if (chinese.matches()) {
            int year = Integer.parseInt(chinese.group(1));
            int number = Integer.parseInt(chinese.group(2));
            if (year >= 2009 && year <= 2030) {
                return new Integer[]{year, number};
            }
            return null;
        }

        // 2026-3
        var dashed = java.util.regex.Pattern
                .compile("^(\\d{4})-(\\d{1,2})$")
                .matcher(normalized);
        if (dashed.matches()) {
            int year = Integer.parseInt(dashed.group(1));
            if (year >= 2009 && year <= 2030) {
                return new Integer[]{year, Integer.parseInt(dashed.group(2))};
            }
        }
        return null;
    }

    /**
     * 从正文里截取关键词附近的片段，供结果列表展示。
     *
     * <p>块内容以 JSON 存储（形如 {"id":…,"type":"paragraph","text":"…"}），
     * 必须先取出真正的文本字段，否则片段里会夹杂 JSON 外壳。
     */
    private String snippet(String contentJson, String keyword) {
        if (contentJson == null || contentJson.isEmpty()) {
            return "";
        }
        String text = extractText(contentJson);
        if (text.isEmpty()) {
            return "";
        }

        // 去掉 Markdown 与公式标记，避免片段里出现大量符号
        String plain = text
                .replaceAll("\\$\\$[^$]*\\$\\$", " ")
                .replaceAll("\\$[^$]*\\$", " ")
                .replaceAll("[#*`>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        int index = plain.toLowerCase().indexOf(keyword.toLowerCase());
        if (index < 0) {
            return plain.length() > 140 ? plain.substring(0, 140) + "…" : plain;
        }
        int start = Math.max(0, index - 40);
        int end = Math.min(plain.length(), index + keyword.length() + 80);
        StringBuilder builder = new StringBuilder();
        if (start > 0) {
            builder.append('…');
        }
        builder.append(plain, start, end);
        if (end < plain.length()) {
            builder.append('…');
        }
        return builder.toString();
    }

    /**
     * 从块的 content_json 里取出可读文本。
     * 覆盖 paragraph / callout / formula / html 等类型；解析失败时退回原始字符串。
     */
    private String extractText(String contentJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    objectMapper.readTree(contentJson);
            for (String field : new String[]{"text", "title", "formula", "caption", "alt"}) {
                com.fasterxml.jackson.databind.JsonNode value = node.get(field);
                if (value != null && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
            com.fasterxml.jackson.databind.JsonNode html = node.get("html");
            if (html != null) {
                // html 块去掉标签后参与搜索展示
                return html.asText().replaceAll("<[^>]+>", " ");
            }
            return "";
        } catch (Exception ex) {
            // 极少数历史数据可能不是合法 JSON，退回原文
            return contentJson;
        }
    }

    /** 搜索建议（工作台输入框下拉用）：只回标题类结果，数量少、响应快。 */
    public List<Map<String, Object>> suggest(String query) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> suggestions = new ArrayList<>();

        Integer[] yearAndNumber = parseYearAndNumber(keyword);
        if (yearAndNumber != null) {
            for (Map<String, Object> question : searchMapper.questionByYearAndNumber(
                    yearAndNumber[0], yearAndNumber[1])) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", "question");
                item.put("label", question.get("title"));
                item.put("hint", question.get("hint"));
                item.put("to", "/practice?examRef=" + question.get("examRef"));
                suggestions.add(item);
            }
            return suggestions;
        }

        for (Map<String, Object> point : searchMapper.searchPoints(keyword, 6)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "point");
            item.put("label", point.get("title"));
            item.put("hint", subjectName(String.valueOf(point.get("subject"))));
            item.put("to", "/learn/" + point.get("pointId"));
            suggestions.add(item);
        }
        for (Map<String, Object> question : searchMapper.searchQuestions(keyword, 4)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "question");
            item.put("label", question.get("title"));
            item.put("hint", question.get("hint"));
            item.put("to", "/practice?examRef=" + question.get("examRef"));
            suggestions.add(item);
        }
        return suggestions;
    }

    private String subjectName(String code) {
        return switch (code) {
            case "ds" -> "数据结构";
            case "os" -> "操作系统";
            case "co" -> "计算机组成原理";
            case "cn" -> "计算机网络";
            default -> code;
        };
    }
}