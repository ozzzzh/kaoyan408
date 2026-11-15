package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.ExamYear;
import com.kaoyan408.entity.MockAnswer;
import com.kaoyan408.entity.MockSession;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionOption;
import com.kaoyan408.entity.QuestionSubQuestion;
import com.kaoyan408.mapper.ExamYearMapper;
import com.kaoyan408.mapper.MockAnswerMapper;
import com.kaoyan408.mapper.MockSessionMapper;
import com.kaoyan408.mapper.QuestionMapper;
import com.kaoyan408.mapper.QuestionOptionMapper;
import com.kaoyan408.mapper.QuestionSubQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟考试：开考、作答、交卷、成绩。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li>考试期间不下发答案与解析。答案只在校验「是否已交卷」之后才返回，
 *       避免用户从网络面板里直接看到答案。</li>
 *   <li>剩余时间由服务端按 {@code startedAt + durationMinutes} 计算，
 *       不信任前端倒计时——刷新页面不能重置时间，也不能靠改系统时钟作弊。</li>
 *   <li>作答随时落库，交卷前的进度不会因为刷新或关页面而丢失。</li>
 * </ol>
 */
@Service
public class MockExamService {

    /** 选择题自动判分满分。408 选择题 40 题 × 2 分。 */
    private static final int CHOICE_FULL_SCORE = 80;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockSessionMapper sessionMapper;
    private final MockAnswerMapper answerMapper;
    private final ExamYearMapper examYearMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionSubQuestionMapper subquestionMapper;
    /** 游客会话（内存态）。登录用户的会话仍走 sessionMapper。 */
    private final GuestMockStore guestStore;

    public MockExamService(MockSessionMapper sessionMapper,
                           MockAnswerMapper answerMapper,
                           ExamYearMapper examYearMapper,
                           QuestionMapper questionMapper,
                           QuestionOptionMapper optionMapper,
                           QuestionSubQuestionMapper subquestionMapper,
                           GuestMockStore guestStore) {
        this.sessionMapper = sessionMapper;
        this.answerMapper = answerMapper;
        this.examYearMapper = examYearMapper;
        this.questionMapper = questionMapper;
        this.optionMapper = optionMapper;
        this.subquestionMapper = subquestionMapper;
        this.guestStore = guestStore;
    }

    /** 开始一场考试。若该用户在此年份已有进行中的场次，直接返回那一场，避免重复开考。 */
    @Transactional
    public Map<String, Object> start(Long userId, Integer year, Integer durationMinutes) {
        ExamYear examYear = examYearMapper.selectOne(
                new QueryWrapper<ExamYear>().eq("year", year).last("LIMIT 1"));
        if (examYear == null) {
            throw new BusinessException(404, "未找到 " + year + " 年的试卷。");
        }

        // 游客：不开数据库记录，直接在内存里建一场。每次都是新的一场
        //（游客没有身份，无法可靠识别「同一个人进行中的场次」）。
        if (userId == null) {
            int minutes = durationMinutes == null ? 180 : durationMinutes;
            MockSession guest = guestStore.create(year,
                    minutes == 0 ? 0 : Math.max(30, Math.min(300, minutes)));
            return state(null, guest.getId());
        }

        MockSession existing = sessionMapper.selectOne(new QueryWrapper<MockSession>()
                .eq("user_id", userId).eq("exam_year", year).eq("status", "ongoing")
                .orderByDesc("id").last("LIMIT 1"));
        if (existing != null) {
            // 进行中的场次若已超时，先按超时交卷，再开新的
            if (isExpired(existing)) {
                submit(userId, existing.getId(), true);
            } else {
                return state(userId, existing.getId());
            }
        }

        MockSession session = new MockSession();
        session.setUserId(userId);
        session.setExamYear(year);
        session.setStartedAt(LocalDateTime.now());
        // 0 表示不限时；否则限定在 30..300 分钟之间，避免误传极端值
        int minutes = durationMinutes == null ? 180 : durationMinutes;
        session.setDurationMinutes(minutes == 0 ? 0 : Math.max(30, Math.min(300, minutes)));
        session.setStatus("ongoing");
        session.setAutoScore(0);
        session.setSelfScore(0);
        session.setTimedOut(0);
        sessionMapper.insert(session);

        return state(userId, session.getId());
    }

    /** 恢复进行中的考试（刷新页面后调用）。 */
    public Map<String, Object> state(Long userId, Long sessionId) {
        MockSession session = requireSession(userId, sessionId);
        boolean submitted = "submitted".equals(session.getStatus());
        boolean guest = userId == null;

        Map<Long, MockAnswer> saved = new LinkedHashMap<>();
        // 游客的作答不落库，答案随会话对象一并带在内存里
        if (guest) {
            for (MockAnswer answer : guestStore.answersOf(session.getId())) {
                saved.put(answer.getQuestionId(), answer);
            }
        } else {
            for (MockAnswer answer : answerMapper.selectList(
                    new QueryWrapper<MockAnswer>().eq("session_id", sessionId))) {
                saved.put(answer.getQuestionId(), answer);
            }
        }

        List<Question> questions = questionsOf(session.getExamYear());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Question question : questions) {
            MockAnswer answer = saved.get(question.getId());
            items.add(questionView(question, answer, submitted));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("examYear", session.getExamYear());
        payload.put("status", session.getStatus());
        payload.put("startedAt", session.getStartedAt());
        payload.put("durationMinutes", session.getDurationMinutes());
        payload.put("remainingSeconds", remainingSeconds(session));
        payload.put("submittedAt", session.getSubmittedAt());
        payload.put("timedOut", session.getTimedOut() != null && session.getTimedOut() == 1);
        payload.put("autoScore", session.getAutoScore());
        payload.put("selfScore", session.getSelfScore());
        payload.put("choiceFullScore", CHOICE_FULL_SCORE);
        payload.put("items", items);
        return payload;
    }

    /** 保存单题答案。考试期间可反复调用；已交卷则拒绝。 */
    @Transactional
    public Map<String, Object> saveAnswer(Long userId, Long sessionId, String examRef,
                                          String answer, Integer selfScore) {
        MockSession session = requireSession(userId, sessionId);
        if ("submitted".equals(session.getStatus())) {
            // 已交卷后仍允许提交自评（综合题批改），但选择题答案不再改动
            Question question = requireQuestion(examRef);
            if (!"comprehensive".equals(question.getQuestionType())) {
                throw new BusinessException(400, "本场考试已交卷，不能再修改选择题答案。");
            }
            return saveSelfScore(session, question, selfScore);
        }
        if (isExpired(session)) {
            throw new BusinessException(400, "本场考试已超时，请交卷后查看成绩。");
        }

        Question question = requireQuestion(examRef);
        boolean guest = userId == null;
        MockAnswer existing = guest
                ? guestStore.answerOf(sessionId, question.getId())
                : answerMapper.selectOne(new QueryWrapper<MockAnswer>()
                        .eq("session_id", sessionId).eq("question_id", question.getId()).last("LIMIT 1"));
        MockAnswer row = existing == null ? new MockAnswer() : existing;
        row.setSessionId(sessionId);
        row.setQuestionId(question.getId());
        if (answer != null) {
            row.setAnswer(answer);
        }
        row.setUpdatedAt(LocalDateTime.now());
        if (guest) {
            // 游客的作答只留在内存里，随会话一起被回收
            guestStore.saveAnswer(sessionId, row);
        } else if (existing == null) {
            answerMapper.insert(row);
        } else {
            answerMapper.updateById(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("saved", true);
        payload.put("remainingSeconds", remainingSeconds(session));
        return payload;
    }

    /** 交卷并判分。选择题自动判，综合题留给用户自评。 */
    @Transactional
    public Map<String, Object> submit(Long userId, Long sessionId, boolean timedOut) {
        MockSession session = requireSession(userId, sessionId);
        if ("submitted".equals(session.getStatus())) {
            return state(userId, sessionId);
        }

        List<Question> questions = questionsOf(session.getExamYear());
        int autoScore = 0;
        boolean guest = userId == null;

        for (Question question : questions) {
            if (!"choice".equals(question.getQuestionType())) {
                continue;
            }
            MockAnswer row = guest
                    ? guestStore.answerOf(sessionId, question.getId())
                    : answerMapper.selectOne(new QueryWrapper<MockAnswer>()
                            .eq("session_id", sessionId).eq("question_id", question.getId()).last("LIMIT 1"));
            if (row == null || row.getAnswer() == null || row.getAnswer().isBlank()) {
                continue;
            }
            boolean correct = question.getAnswer() != null
                    && question.getAnswer().trim().equalsIgnoreCase(row.getAnswer().trim());
            row.setCorrect(correct ? 1 : 0);
            row.setUpdatedAt(LocalDateTime.now());
            if (guest) {
                guestStore.saveAnswer(sessionId, row);
            } else {
                answerMapper.updateById(row);
            }
            if (correct) {
                autoScore += question.getScore() == null ? 0 : question.getScore();
            }
        }

        // 超时交卷也算作超时；手动交卷在到期前则不算
        boolean actualTimeout = timedOut || isExpired(session);
        session.setStatus("submitted");
        session.setSubmittedAt(LocalDateTime.now());
        session.setAutoScore(autoScore);
        session.setTimedOut(actualTimeout ? 1 : 0);
        // 游客会话是内存对象，改完即生效，不需要（也不能）写库
        if (!guest) {
            sessionMapper.updateById(session);
        }

        return state(userId, sessionId);
    }

    /** 综合题自评：把用户给的分数写进作答行，并刷新会话总分。 */
    @Transactional
    public Map<String, Object> gradeComprehensive(Long userId, Long sessionId,
                                                  String examRef, Integer selfScore) {
        MockSession session = requireSession(userId, sessionId);
        if (!"submitted".equals(session.getStatus())) {
            throw new BusinessException(400, "请先交卷，再进行综合题自评。");
        }
        return saveSelfScore(session, requireQuestion(examRef), selfScore);
    }

    /** 历史成绩列表。 */
    public Map<String, Object> history(Long userId, int limit) {
        List<Map<String, Object>> rows = sessionMapper.history(userId, Math.max(1, Math.min(50, limit)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", rows.size());
        payload.put("items", rows);
        return payload;
    }

    /* ---------- 内部方法 ---------- */

    private Map<String, Object> saveSelfScore(MockSession session, Question question, Integer selfScore) {
        if (!"comprehensive".equals(question.getQuestionType())) {
            throw new BusinessException(400, "只有综合题可以自评。");
        }
        int full = question.getScore() == null ? 0 : question.getScore();
        int score = selfScore == null ? 0 : Math.max(0, Math.min(full, selfScore));
        boolean guest = session.getId() != null && session.getId() < 0;

        MockAnswer row = guest
                ? guestStore.answerOf(session.getId(), question.getId())
                : answerMapper.selectOne(new QueryWrapper<MockAnswer>()
                        .eq("session_id", session.getId()).eq("question_id", question.getId()).last("LIMIT 1"));
        boolean isNew = row == null;
        if (isNew) {
            row = new MockAnswer();
            row.setSessionId(session.getId());
            row.setQuestionId(question.getId());
        }
        row.setSelfScore(score);
        row.setUpdatedAt(LocalDateTime.now());
        if (guest) {
            guestStore.saveAnswer(session.getId(), row);
        } else if (isNew) {
            answerMapper.insert(row);
        } else {
            answerMapper.updateById(row);
        }

        // 重算整场自评总分（避免累加漂移）
        List<MockAnswer> all = guest
                ? guestStore.answersOf(session.getId())
                : answerMapper.selectList(
                        new QueryWrapper<MockAnswer>().eq("session_id", session.getId()));
        int total = 0;
        for (MockAnswer item : all) {
            if (item.getSelfScore() != null) {
                total += item.getSelfScore();
            }
        }
        session.setSelfScore(total);
        if (!guest) {
            sessionMapper.updateById(session);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("selfScore", score);
        payload.put("sessionSelfScore", total);
        return payload;
    }

    private MockSession requireSession(Long userId, Long sessionId) {
        // 游客会话 id 为负、只在内存；登录用户会话 id 为正、走数据库。
        // 两条路径都要校验归属，否则拿到别人的 id 就能看别人的卷。
        if (userId == null) {
            MockSession guest = guestStore.get(sessionId);
            if (guest == null) {
                throw new BusinessException(404, "未找到该场考试（游客场次不保留，请重新开始）。");
            }
            return guest;
        }
        MockSession session = sessionId == null || sessionId < 0
                ? null
                : sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(404, "未找到该场考试。");
        }
        return session;
    }

    private Question requireQuestion(String examRef) {
        Question question = questionMapper.selectOne(
                new QueryWrapper<Question>().eq("exam_ref", examRef).last("LIMIT 1"));
        if (question == null) {
            throw new BusinessException(404, "未找到该题目：" + examRef);
        }
        return question;
    }

    private List<Question> questionsOf(Integer year) {
        ExamYear examYear = examYearMapper.selectOne(
                new QueryWrapper<ExamYear>().eq("year", year).last("LIMIT 1"));
        if (examYear == null) {
            return List.of();
        }
        return questionMapper.selectList(new QueryWrapper<Question>()
                .eq("exam_id", examYear.getId()).orderByAsc("exam_number"));
    }

    /** 是否已超过限时。durationMinutes=0 表示不限时。 */
    private boolean isExpired(MockSession session) {
        Integer minutes = session.getDurationMinutes();
        if (minutes == null || minutes == 0 || session.getStartedAt() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(session.getStartedAt().plusMinutes(minutes));
    }

    /** 解析库里以 JSON 数组存的图片路径；空值或格式异常一律返回空列表，不让单题坏数据拖垮整卷。 */
    private List<String> readJsonList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 剩余秒数；不限时返回 -1。已超时返回 0。 */
    private long remainingSeconds(MockSession session) {
        Integer minutes = session.getDurationMinutes();
        if (minutes == null || minutes == 0) {
            return -1;
        }
        if ("submitted".equals(session.getStatus())) {
            return 0;
        }
        long elapsed = Duration.between(session.getStartedAt(), LocalDateTime.now()).getSeconds();
        long total = minutes * 60L;
        return Math.max(0, total - elapsed);
    }

    /**
     * 题目视图。考试进行中绝不返回 answer / explanation / options 的正确标记，
     * 交卷后才附带答案与解析供对答案。
     */
    private Map<String, Object> questionView(Question question, MockAnswer answer, boolean submitted) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", question.getExamRef());
        view.put("number", question.getExamNumber());
        view.put("subject", question.getSubject());
        view.put("chapterName", question.getChapterName());
        view.put("questionType", question.getQuestionType());
        view.put("score", question.getScore());
        view.put("stem", question.getStem());
        view.put("imagePaths", readJsonList(question.getImagePathsJson()));
        view.put("myAnswer", answer == null ? null : answer.getAnswer());
        view.put("correct", answer == null ? null : answer.getCorrect());
        view.put("selfScore", answer == null ? null : answer.getSelfScore());

        List<Map<String, Object>> options = new ArrayList<>();
        if ("choice".equals(question.getQuestionType())) {
            for (QuestionOption option : optionMapper.selectList(new QueryWrapper<QuestionOption>()
                    .eq("question_id", question.getId()).orderByAsc("option_key"))) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", option.getOptionKey());
                item.put("text", option.getOptionText());
                options.add(item);
            }
        }
        view.put("options", options);

        List<Map<String, Object>> subs = new ArrayList<>();
        for (QuestionSubQuestion sub : subquestionMapper.selectList(
                new QueryWrapper<QuestionSubQuestion>()
                        .eq("question_id", question.getId()).orderByAsc("sub_number"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subNumber", sub.getSubNumber());
            item.put("score", sub.getScore());
            item.put("description", sub.getDescription());
            subs.add(item);
        }
        view.put("subQuestions", subs);

        // 答案与解析只在交卷后下发——这是模拟考试的核心约束
        if (submitted) {
            view.put("standardAnswer", question.getAnswer());
            view.put("explanation", question.getExplanation());
        }
        return view;
    }
}