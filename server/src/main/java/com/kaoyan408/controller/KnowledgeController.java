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
        blockView.put("content", block.getContentJson());

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
            view.put("content", block.getContentJson());
            view.put("questionCount", linkCounts.getOrDefault(block.getBlockId(), 0L));
            return view;
        }).toList());

        // 该知识点关联真题的去重总数。
        // 不能用各块关联数相加：一题可能挂在同一知识点的多个块上，相加会虚高
        //（实测 co-vm-impl 求和 468 / 去重 61）。
        payload.put("questionCount", questionMapper.distinctQuestionsOfPoint(pointId));
        return Result.ok(payload);
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