package com.kaoyan408.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionOption;
import com.kaoyan408.entity.QuestionSubQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan408.mapper.QuestionOptionMapper;
import com.kaoyan408.mapper.QuestionSubQuestionMapper;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.QuestionService;
import com.kaoyan408.service.StatsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.kaoyan408.entity.User;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单题读取与作答。解析默认不下发，作答后（或已作答 + reveal=1）才返回。
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QuestionService questionService;
    private final QuestionOptionMapper optionMapper;
    private final QuestionSubQuestionMapper subQuestionMapper;
    private final AuthService authService;
    private final StatsService statsService;

    public QuestionController(QuestionService questionService, QuestionOptionMapper optionMapper,
                              QuestionSubQuestionMapper subQuestionMapper, AuthService authService,
                              StatsService statsService) {
        this.questionService = questionService;
        this.optionMapper = optionMapper;
        this.subQuestionMapper = subQuestionMapper;
        this.authService = authService;
        this.statsService = statsService;
    }

    public record AnswerRequest(String answer, Integer durationMs) {
    }

    @GetMapping("/{examRef}")
    public Result<Map<String, Object>> detail(@PathVariable String examRef,
                                              @RequestParam(defaultValue = "0") int reveal,
                                              Authentication authentication) {
        Question question = questionService.requireByRef(examRef);
        Long userId = userIdOf(authentication);
        boolean answered = userId != null && questionService.attemptOf(userId, question.getId()) != null;
        boolean withExplanation = answered && (reveal == 0 || reveal == 1);
        return Result.ok(view(question, withExplanation, answered));
    }

    @PostMapping("/{examRef}/answer")
    public Result<Map<String, Object>> answer(@PathVariable String examRef,
                                              @RequestBody(required = false) AnswerRequest request,
                                              Authentication authentication) {
        Question question = questionService.requireByRef(examRef);
        Long userId = userIdOf(authentication);
        String submitted = request == null ? null : request.answer();
        Integer duration = request == null ? null : request.durationMs();
        return Result.ok(questionService.submit(userId, question, submitted, duration));
    }

    /** 单题当前用户状态（是否已作答），供练习页快速判断。 */
    @GetMapping("/{examRef}/status")
    public Result<Map<String, Object>> status(@PathVariable String examRef, Authentication authentication) {
        Question question = questionService.requireByRef(examRef);
        Long userId = userIdOf(authentication);
        var attempt = questionService.attemptOf(userId, question.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answered", attempt != null);
        payload.put("correct", attempt != null && attempt.getResult() != null && attempt.getResult() == 1);
        payload.put("stats", statsService.dashboard(userId));
        return Result.ok(payload);
    }

    private Map<String, Object> view(Question question, boolean withExplanation, boolean answered) {
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
        view.put("knowledgeBlockIds", questionService.blockIdsOf(question.getId()));
        // 带可读名称的关联知识点，供界面直接展示与跳转
        view.put("knowledgeBlocks", questionService.blocksOf(question.getId()));
        view.put("answered", answered);
        view.put("options", optionViews(question));
        view.put("subQuestions", subQuestionViews(question));
        if (withExplanation) {
            view.put("answer", question.getAnswer());
            view.put("explanation", question.getExplanation());
        }
        return view;
    }

    private List<Map<String, Object>> optionViews(Question question) {
        if (!"choice".equals(question.getQuestionType())) {
            return Collections.emptyList();
        }
        return optionMapper.selectList(new QueryWrapper<QuestionOption>()
                        .eq("question_id", question.getId()).orderByAsc("option_key")).stream()
                .map(option -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", option.getOptionKey());
                    item.put("text", option.getOptionText());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> subQuestionViews(Question question) {
        return subQuestionMapper.selectList(new QueryWrapper<QuestionSubQuestion>()
                        .eq("question_id", question.getId()).orderByAsc("sub_number")).stream()
                .map(sub -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("number", sub.getSubNumber());
                    item.put("score", sub.getScore());
                    item.put("description", sub.getDescription());
                    return item;
                })
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

    private Long userIdOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        User user = authService.findByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }
}