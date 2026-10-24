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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 年份列表 + 每卷题量 + 每卷四科分布。 */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<ExamYear> years = examYearMapper.selectList(new QueryWrapper<ExamYear>().orderByAsc("year"));
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ExamYear year : years) {
            List<Question> questions = questionsOf(year.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("year", year.getYear());
            item.put("title", year.getTitle());
            item.put("questionCount", questions.size());
            item.put("subjects", subjectDistribution(questions));
            payload.add(item);
        }
        return Result.ok(payload);
    }

    /** 某一年整卷（不含答案与解析，答案通过做题接口校验）。 */
    @GetMapping("/{year}")
    public Result<Map<String, Object>> detail(@PathVariable Integer year) {
        ExamYear exam = examYearMapper.selectOne(new QueryWrapper<ExamYear>().eq("year", year).last("LIMIT 1"));
        if (exam == null) {
            throw new BusinessException(404, "未找到该年份的试卷：" + year);
        }
        List<Question> questions = questionsOf(exam.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("year", exam.getYear());
        payload.put("title", exam.getTitle());
        payload.put("questionCount", questions.size());
        payload.put("questions", questions.stream().map(this::toQuestionView).toList());
        return Result.ok(payload);
    }

    private List<Question> questionsOf(Long examId) {
        return questionMapper.selectList(new QueryWrapper<Question>()
                .eq("exam_id", examId)
                .orderByAsc("exam_number"));
    }

    private Map<String, Long> subjectDistribution(List<Question> questions) {
        return questions.stream().collect(Collectors.groupingBy(
                Question::getSubject, LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Object> toQuestionView(Question question) {
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
        view.put("knowledgeBlockIds", knowledgeBlockIdsOf(question.getId()));
        view.put("knowledgeBlocks", questionService.blocksOf(question.getId()));
        if ("choice".equals(question.getQuestionType())) {
            view.put("options", optionMapper.selectList(new QueryWrapper<QuestionOption>()
                            .eq("question_id", question.getId())
                            .orderByAsc("option_key")).stream()
                    .map(option -> Map.of("key", option.getOptionKey(), "text", option.getOptionText()))
                    .toList());
        } else {
            view.put("options", Collections.emptyList());
        }
        return view;
    }

    private List<String> knowledgeBlockIdsOf(Long questionId) {
        return questionKnowledgeMapper.selectList(new QueryWrapper<QuestionKnowledge>()
                        .eq("question_id", questionId)).stream()
                .map(QuestionKnowledge::getBlockId)
                .toList();
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