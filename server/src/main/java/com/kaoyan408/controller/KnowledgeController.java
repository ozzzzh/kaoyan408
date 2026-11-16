package com.kaoyan408.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.KnowledgeBlock;
import com.kaoyan408.entity.KnowledgePoint;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionKnowledge;
import com.kaoyan408.mapper.KnowledgeBlockMapper;
import com.kaoyan408.mapper.KnowledgePointMapper;
import com.kaoyan408.mapper.QuestionKnowledgeMapper;
import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识正文读取。块内容为 JSON 字符串，前端按 type 渲染（段落/公式/动画等）。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final KnowledgePointMapper pointMapper;
    private final KnowledgeBlockMapper blockMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final QuestionMapper questionMapper;

    public KnowledgeController(KnowledgePointMapper pointMapper, KnowledgeBlockMapper blockMapper,
                               QuestionKnowledgeMapper questionKnowledgeMapper, QuestionMapper questionMapper) {
        this.pointMapper = pointMapper;
        this.blockMapper = blockMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.questionMapper = questionMapper;
    }

    /** 知识点清单（按科目分组），含块数与关联真题数。 */
    @GetMapping
    public Result<List<Map<String, Object>>> tree(@RequestParam(required = false) String subject) {
        QueryWrapper<KnowledgePoint> query = new QueryWrapper<KnowledgePoint>().orderByAsc("sort_order");
        if (subject != null && !subject.isBlank()) {
            query.eq("subject", subject);
        }
        List<KnowledgePoint> points = pointMapper.selectList(query);
        Map<String, Long> blockCounts = countByPoint(blockMapper.selectList(new QueryWrapper<>()));
        List<Map<String, Object>> payload = new ArrayList<>();
        for (KnowledgePoint point : points) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pointId", point.getPointId());
            item.put("subject", point.getSubject());
            item.put("title", point.getTitle());
            item.put("blockCount", blockCounts.getOrDefault(point.getPointId(), 0L));
            payload.add(item);
        }
        return Result.ok(payload);
    }

    /** 单个知识块 + 关联真题（真题只回题号，不含解析）。 */
    @GetMapping("/block/{blockId}")
    public Result<Map<String, Object>> block(@PathVariable String blockId) {
        KnowledgeBlock block = blockMapper.selectOne(new QueryWrapper<KnowledgeBlock>()
                .eq("block_id", blockId).last("LIMIT 1"));
        if (block == null) {
            throw new BusinessException(404, "未找到该知识块：" + blockId);
        }
        KnowledgePoint point = pointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                .eq("point_id", block.getPointId()).last("LIMIT 1"));

        Map<String, Object> blockView = new LinkedHashMap<>();
        blockView.put("blockId", block.getBlockId());
        blockView.put("pointId", block.getPointId());
        blockView.put("pointTitle", point == null ? null : point.getTitle());
        blockView.put("subpointId", block.getSubpointId());
        blockView.put("subpointTitle", block.getSubpointTitle());
        blockView.put("type", block.getType());
        blockView.put("content", externalizeInlineImages(block));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("block", blockView);
        payload.put("relatedQuestions", relatedQuestions(blockId));
        return Result.ok(payload);
    }

    /** 某知识点下的全部块（按顺序），供知识页一次拉取。 */
    @GetMapping("/point/{pointId}")
    public Result<Map<String, Object>> point(@PathVariable String pointId) {
        List<KnowledgeBlock> blocks = blockMapper.selectList(new QueryWrapper<KnowledgeBlock>()
                .eq("point_id", pointId).orderByAsc("block_order"));
        if (blocks.isEmpty()) {
            throw new BusinessException(404, "未找到该知识点的正文：" + pointId);
        }
        KnowledgePoint point = pointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                .eq("point_id", pointId).last("LIMIT 1"));

        // 每个知识块关联的真题数（一次批量查询，避免 N+1）
        List<String> blockIds = blocks.stream().map(KnowledgeBlock::getBlockId).toList();
        Map<String, Long> linkCounts = new LinkedHashMap<>();
        if (!blockIds.isEmpty()) {
            for (Map<String, Object> row : questionMapper.questionCountsOfBlocks(blockIds)) {
                linkCounts.put(String.valueOf(row.get("blockId")), toLong(row.get("total")));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pointId", pointId);
        payload.put("title", point == null ? null : point.getTitle());
        payload.put("subject", point == null ? null : point.getSubject());
        payload.put("blocks", blocks.stream().map(block -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("blockId", block.getBlockId());
            view.put("subpointId", block.getSubpointId());
            view.put("subpointTitle", block.getSubpointTitle());
            view.put("order", block.getBlockOrder());
            view.put("type", block.getType());
            view.put("content", externalizeInlineImages(block));
            view.put("questionCount", linkCounts.getOrDefault(block.getBlockId(), 0L));
            return view;
        }).toList());

        // 该知识点关联真题的去重总数。
        // 不能用各块关联数相加：一题可能挂在同一知识点的多个块上，相加会虚高
        //（实测 co-vm-impl 求和 468 / 去重 61）。
        payload.put("questionCount", questionMapper.distinctQuestionsOfPoint(pointId));
        return Result.ok(payload);
    }

    /**
     * 知识块里的内联图片资源。
     *
     * <p>正文图片原来以 base64 内联在 content_json 里，单个块可达 324KB：
     * /knowledge/point 一次要下发全部块，于是最重的知识点响应 340KB，
     * 而其中 324KB 是一张图——它既不能单独缓存，也不能渐进显示。
     *
     * <p>现在响应里把它改写成带内容指纹的 URL，图片由这个端点单独提供：
     * JSON 只剩几百字节，图片走浏览器/边缘缓存（immutable），后续访问零成本。
     */
    @GetMapping("/asset/{blockId}")
    public org.springframework.http.ResponseEntity<byte[]> asset(@PathVariable String blockId) {
        KnowledgeBlock block = blockMapper.selectOne(new QueryWrapper<KnowledgeBlock>()
                .eq("block_id", blockId).last("LIMIT 1"));
        if (block == null) {
            throw new BusinessException(404, "未找到该资源：" + blockId);
        }
        String src = readJsonField(block.getContentJson(), "src");
        if (src == null) {
            throw new BusinessException(404, "该块没有内联资源：" + blockId);
        }
        byte[] bytes;
        String mime;
        if (src.startsWith("data:")) {
            int comma = src.indexOf(',');
            if (comma < 0) {
                throw new BusinessException(500, "资源格式异常");
            }
            mime = src.substring(5, comma).split(";")[0];
            bytes = java.util.Base64.getMimeDecoder().decode(src.substring(comma + 1));
        } else if (src.stripLeading().startsWith("<svg")) {
            // 题库里有一部分「图片」块其实是内联 SVG 源码（最大的一个 324KB），
            // 原来它以字符串形式塞在 JSON 里，<img src> 根本渲染不出来。
            mime = "image/svg+xml";
            bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            throw new BusinessException(404, "该块没有内联资源：" + blockId);
        }
        if (!mime.startsWith("image/")) {
            // 只提供图片，避免这个端点变成任意内容的出口
            throw new BusinessException(400, "仅支持图片资源");
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(mime))
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
                // SVG 可能带脚本：CSP sandbox 让它在被直接打开时也无法执行脚本
                .header("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    /**
     * 把 image 块里的 data: URI 换成本地资源 URL（带内容指纹，便于长缓存）。
     * 非图片块原样返回，不额外解析 JSON。
     */
    private String externalizeInlineImages(KnowledgeBlock block) {
        String json = block.getContentJson();
        if (!"image".equals(block.getType()) || json == null) {
            return json;
        }
        // 两种内联形态都要外置：data: URI（base64）与内联 SVG 源码。
        if (!json.contains("data:") && !json.contains("<svg")) {
            return json;
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(json);
            String src = node.path("src").asText("");
            if (src.startsWith("data:") || src.stripLeading().startsWith("<svg")) {
                String version = sha256Short(src);
                node.put("src", "/api/v1/knowledge/asset/" + block.getBlockId() + "?v=" + version);
            }
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            // 解析失败就原样返回：宁可用大一点的响应，也不要让正文渲染不出来
            return json;
        }
    }

    private String readJsonField(String json, String field) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readTree(json).path(field).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256Short(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            return "0";
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private List<Map<String, Object>> relatedQuestions(String blockId) {
        List<QuestionKnowledge> links = questionKnowledgeMapper.selectList(
                new QueryWrapper<QuestionKnowledge>().eq("block_id", blockId));
        if (links.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = links.stream().map(QuestionKnowledge::getQuestionId).toList();
        List<Question> questions = questionMapper.selectBatchIds(ids);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Question question : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", question.getExamRef());
            item.put("number", question.getExamNumber());
            item.put("year", yearOf(question));
            item.put("subject", question.getSubject());
            item.put("questionType", question.getQuestionType());
            item.put("hasImage", question.getImagePathsJson() != null && question.getImagePathsJson().length() > 2);
            payload.add(item);
        }
        return payload;
    }

    private Object yearOf(Question question) {
        if (question.getExamRef() == null || question.getExamRef().length() < 10) {
            return null;
        }
        String[] parts = question.getExamRef().split("-");
        return parts.length >= 2 ? parts[1] : null;
    }

    private Map<String, Long> countByPoint(List<KnowledgeBlock> blocks) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (KnowledgeBlock block : blocks) {
            counts.merge(block.getPointId(), 1L, Long::sum);
        }
        return counts;
    }
}