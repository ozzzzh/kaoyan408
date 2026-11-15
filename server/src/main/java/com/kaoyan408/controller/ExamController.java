package com.kaoyan408.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.ExamYear;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionOption;
import com.kaoyan408.mapper.ExamYearMapper;
import com.kaoyan408.mapper.QuestionKnowledgeMapper;
import com.kaoyan408.mapper.QuestionMapper;
import com.kaoyan408.mapper.QuestionOptionMapper;
import com.kaoyan408.entity.QuestionKnowledge;
import com.kaoyan408.service.QuestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真题试卷读取。真题内容以 408os 为准（题面/选项/答案/解析同源）。
 */
@RestController
@RequestMapping("/api/v1/exams")
public class ExamController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExamYearMapper examYearMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final QuestionService questionService;

    public ExamController(ExamYearMapper examYearMapper, QuestionMapper questionMapper,
                          QuestionOptionMapper optionMapper, QuestionKnowledgeMapper questionKnowledgeMapper,
                          QuestionService questionService) {
        this.examYearMapper = examYearMapper;
        this.questionMapper = questionMapper;
        this.optionMapper = optionMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.questionService = questionService;
    }

    /**
     * 年份列表 + 每卷题量 + 每卷四科分布。
     *
     * <p>原来逐年份 selectList 把整卷题目（含 MEDIUMTEXT 的题干与解析）读进内存再数，
     * 18 卷就是 18 次全字段查询。这里一条聚合 SQL 就够。
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        Map<Integer, Map<String, Object>> byYear = new LinkedHashMap<>();
        for (Map<String, Object> row : questionMapper.examYearStats()) {
            Integer year = row.get("year") == null ? null : ((Number) row.get("year")).intValue();
            Map<String, Object> item = byYear.get(year);
            if (item == null) {
                item = new LinkedHashMap<>();
                item.put("year", year);
                item.put("title", row.get("title"));
                item.put("questionCount", 0L);
                item.put("subjects", new LinkedHashMap<String, Long>());
                byYear.put(year, item);
            }
            long count = row.get("total") == null ? 0 : ((Number) row.get("total")).longValue();
            item.put("questionCount", (Long) item.get("questionCount") + count);
            @SuppressWarnings("unchecked")
            Map<String, Long> subjects = (Map<String, Long>) item.get("subjects");
            Object subject = row.get("subject");
            if (subject != null) {
                subjects.put(String.valueOf(subject), count);
            }
        }
        return Result.ok(new ArrayList<>(byYear.values()));
    }

    /**
     * 某一年整卷（不含答案与解析，答案通过做题接口校验）。
     *
     * <p>逐题查选项/知识块会造成 150 条以上的查询（47 题 × 3~4 条）。这里一次性
     * 把整卷的选项、知识点关联、知识块名称批量取回再按题分组。
     */
    @GetMapping("/{year}")
    public Result<Map<String, Object>> detail(@PathVariable Integer year) {
        ExamYear exam = examYearMapper.selectOne(new QueryWrapper<ExamYear>().eq("year", year).last("LIMIT 1"));
        if (exam == null) {
            throw new BusinessException(404, "未找到该年份的试卷：" + year);
        }
        List<Question> questions = questionsOf(exam.getId());
        List<Long> questionIds = questions.stream().map(Question::getId).toList();

        Map<Long, List<Map<String, Object>>> optionsByQuestion = optionsOf(questionIds);
        Map<Long, List<String>> blockIdsByQuestion = blockIdsOf(questionIds);
        Map<Long, List<Map<String, Object>>> blocksByQuestion =
                questionService.blocksOfMany(blockIdsByQuestion);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("year", exam.getYear());
        payload.put("title", exam.getTitle());
        payload.put("questionCount", questions.size());
        payload.put("questions", questions.stream()
                .map(q -> toQuestionView(q,
                        optionsByQuestion.getOrDefault(q.getId(), Collections.emptyList()),
                        blockIdsByQuestion.getOrDefault(q.getId(), Collections.emptyList()),
                        blocksByQuestion.getOrDefault(q.getId(), Collections.emptyList())))
                .toList());
        return Result.ok(payload);
    }

    /** 整卷选项：一条 IN 查询后按题目分组。 */
    private Map<Long, List<Map<String, Object>>> optionsOf(List<Long> questionIds) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        if (questionIds.isEmpty()) {
            return result;
        }
        for (QuestionOption option : optionMapper.selectList(new QueryWrapper<QuestionOption>()
                .in("question_id", questionIds).orderByAsc("option_key"))) {
            result.computeIfAbsent(option.getQuestionId(), k -> new ArrayList<>())
                    .add(Map.of("key", option.getOptionKey(), "text", option.getOptionText()));
        }
        return result;
    }

    /** 整卷的题目↔知识块关联：一条 IN 查询后按题目分组。 */
    private Map<Long, List<String>> blockIdsOf(List<Long> questionIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (questionIds.isEmpty()) {
            return result;
        }
        for (QuestionKnowledge link : questionKnowledgeMapper.selectList(new QueryWrapper<QuestionKnowledge>()
                .in("question_id", questionIds))) {
            result.computeIfAbsent(link.getQuestionId(), k -> new ArrayList<>()).add(link.getBlockId());
        }
        return result;
    }

    private List<Question> questionsOf(Long examId) {
        return questionMapper.selectList(new QueryWrapper<Question>()
                .eq("exam_id", examId)
                .orderByAsc("exam_number"));
    }


    private Map<String, Object> toQuestionView(Question question,
                                               List<Map<String, Object>> options,
                                               List<String> knowledgeBlockIds,
                                               List<Map<String, Object>> knowledgeBlocks) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", question.getExamRef());
        view.put("number", question.getExamNumber());
        view.put("subject", question.getSubject());
        view.put("chapterName", question.getChapterName());
        view.put("questionType", question.getQuestionType());
        view.put("score", question.getScore());
        view.put("stem", question.getStem());
        view.put("difficulty", question.getDifficulty());
        view.put("tags", readJsonList(question.getTagsJson()));
        view.put("imagePaths", readJsonList(question.getImagePathsJson()));
        view.put("knowledgeBlockIds", knowledgeBlockIds);
        view.put("knowledgeBlocks", knowledgeBlocks);
        view.put("options", "choice".equals(question.getQuestionType()) ? options : Collections.emptyList());
        return view;
    }

    private List<String> readJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}