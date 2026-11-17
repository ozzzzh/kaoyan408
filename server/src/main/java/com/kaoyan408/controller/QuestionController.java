package com.kaoyan408.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.core.Result;
import com.kaoyan408.entity.Question;
import com.kaoyan408.service.AuthService;
import com.kaoyan408.service.QuestionService;
import com.kaoyan408.service.StatsService;
import com.kaoyan408.service.VideoGuideService;
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

    private final QuestionService questionService;
    private final AuthService authService;
    private final StatsService statsService;
    private final VideoGuideService videoGuideService;

    public QuestionController(QuestionService questionService, AuthService authService,
                              StatsService statsService, VideoGuideService videoGuideService) {
        this.questionService = questionService;
        this.authService = authService;
        this.statsService = statsService;
        this.videoGuideService = videoGuideService;
    }

    public record AnswerRequest(String answer, Integer durationMs) {
    }

    /**
     * 单题详情。
     *
     * <p>默认只在「已作答」时返回答案与解析——这是做题模式的核心约束：
     * 把答案藏在响应里，用浏览器网络面板一眼就能看到，练习就没意义了。
     *
     * <p>背题模式（{@code memorize=true}）直接返回答案：该模式的目的就是
     * 先看答案建立印象，遮住答案反而没法用。只有「整卷背题」这类显式入口
     * 才会传这个参数，不影响普通做题。
     */
    @GetMapping("/{examRef}")
    public Result<Map<String, Object>> detail(@PathVariable String examRef,
                                              @RequestParam(defaultValue = "false") boolean memorize,
                                              Authentication authentication) {
        Long userId = userIdOf(authentication);
        // 视图组装已下沉到 QuestionService.detailOf：刷题队列要用同一份逻辑内联首题。
        return Result.ok(questionService.detailOf(examRef, userId, memorize));
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

    public record SelfScoreRequest(Integer score) {
    }

    /**
     * 综合题自评。
     *
     * <p>408 综合题占 70 分，没有可字符串比对的标准答案，只能由考生对照参考答案
     * 自行打分。写入后该题算「已作答」，计入已覆盖分值，但不参与正确率。
     */
    @PostMapping("/{examRef}/self-score")
    public Result<Map<String, Object>> selfScore(@PathVariable String examRef,
                                                 @RequestBody(required = false) SelfScoreRequest request,
                                                 Authentication authentication) {
        Question question = questionService.requireByRef(examRef);
        Long userId = userIdOf(authentication);
        return Result.ok(questionService.selfScore(userId, question, request == null ? null : request.score()));
    }
    /**
     * 视频指引：按该题关联的知识点，生成到 B 站的搜索入口。
     *
     * <p>不返回任何第三方课程的播放地址：本项目不托管、也不转存课程内容，
     * 只依据自有数据告诉用户「该去搜什么」。
     */
    @GetMapping("/{examRef}/video-guide")
    public Result<Map<String, Object>> videoGuide(@PathVariable String examRef) {
        return Result.ok(videoGuideService.guideOf(examRef));
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

    private Long userIdOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        User user = authService.findByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }
}
