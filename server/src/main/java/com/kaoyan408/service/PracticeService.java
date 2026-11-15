package com.kaoyan408.service;

import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷题队列与错题本。
 */
@Service
public class PracticeService {

    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_WRONG = "wrong";
    private static final String SCOPE_UNTOUCHED = "untouched";
    /** 错题本中尚未订正的错题 */
    private static final String SCOPE_WRONG_PENDING = "wrong-pending";
    /** 错题本中已订正掌握的错题（用于回顾） */
    private static final String SCOPE_WRONG_MASTERED = "wrong-mastered";

    private static final String[] SUBJECTS = {"ds", "os", "co", "cn"};
    private static final Map<String, String> SUBJECT_NAMES = Map.of(
            "ds", "数据结构",
            "os", "操作系统",
            "co", "计算机组成原理",
            "cn", "计算机网络");

    private final QuestionMapper questionMapper;

    public PracticeService(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    /**
     * 刷题队列。scope 决定取全部 / 仅错题 / 仅未做；
     * wrong 与 untouched 需要登录（游客没有作答记录，按未做处理）。
     */
    public Map<String, Object> queue(Long userId, Integer year, String subject,
                                     String blockId, String pointId, String scope) {
        // wrong = 所有错过的题（含已订正）；wrong-pending = 待订正；wrong-mastered = 已订正
        boolean onlyWrong = SCOPE_WRONG.equalsIgnoreCase(scope);
        boolean onlyUntouched = SCOPE_UNTOUCHED.equalsIgnoreCase(scope);
        boolean onlyPendingWrong = SCOPE_WRONG_PENDING.equalsIgnoreCase(scope);
        boolean onlyMasteredWrong = SCOPE_WRONG_MASTERED.equalsIgnoreCase(scope);

        // 游客看错题本没有意义，直接回空队列而不是回全量
        if ((onlyWrong || onlyPendingWrong || onlyMasteredWrong) && userId == null) {
            return payload(List.of(), year, subject, blockId, pointId, scope);
        }

        List<Map<String, Object>> rows = questionMapper.queue(
                userId, year, isEmpty(subject) ? null : subject,
                isEmpty(blockId) ? null : blockId,
                isEmpty(pointId) ? null : pointId,
                onlyWrong, onlyUntouched, onlyPendingWrong, onlyMasteredWrong);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("examRef", row.get("examRef"));
            item.put("year", row.get("year"));
            item.put("number", row.get("number"));
            item.put("subject", row.get("subject"));
            item.put("chapterName", row.get("chapterName"));
            item.put("questionType", row.get("questionType"));
            item.put("score", row.get("score"));
            item.put("difficulty", row.get("difficulty"));
            Object result = row.get("result");
            item.put("answered", result != null);
            item.put("correct", result != null && ((Number) result).intValue() == 1);
            // 错题闭环状态
            item.put("wrongCount", toLong(row.get("wrongCount")));
            item.put("retryCorrectCount", toLong(row.get("retryCorrectCount")));
            item.put("mastered", row.get("masteredAt") != null);
            items.add(item);
        }
        return payload(items, year, subject, blockId, pointId, scope);
    }

    private Map<String, Object> payload(List<Map<String, Object>> items, Integer year, String subject,
                                        String blockId, String pointId, String scope) {
        long answered = items.stream().filter(i -> Boolean.TRUE.equals(i.get("answered"))).count();
        long correct = items.stream().filter(i -> Boolean.TRUE.equals(i.get("correct"))).count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("year", year);
        payload.put("subject", subject);
        payload.put("blockId", blockId);
        payload.put("pointId", pointId);
        payload.put("total", items.size());
        payload.put("answered", answered);
        payload.put("correct", correct);
        payload.put("items", items);
        return payload;
    }

    /**
     * 错题本：待订正 / 已订正的数量与分科目分布。
     * 待订正 = 曾答错且尚未在重做中答对；已订正 = 重做已答对。
     */
    public Map<String, Object> wrongBook(Long userId) {
        List<Map<String, Object>> pending = userId == null
                ? List.of()
                : questionMapper.queue(userId, null, null, null, null, false, false, true, false);
        List<Map<String, Object>> mastered = userId == null
                ? List.of()
                : questionMapper.queue(userId, null, null, null, null, false, false, false, true);

        Map<String, Long> pendingBySubject = new LinkedHashMap<>();
        Map<String, Long> masteredBySubject = new LinkedHashMap<>();
        for (Map<String, Object> row : pending) {
            pendingBySubject.merge(String.valueOf(row.get("subject")), 1L, Long::sum);
        }
        for (Map<String, Object> row : mastered) {
            masteredBySubject.merge(String.valueOf(row.get("subject")), 1L, Long::sum);
        }

        List<Map<String, Object>> subjects = new ArrayList<>();
        for (String subject : SUBJECTS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", subject);
            item.put("name", SUBJECT_NAMES.getOrDefault(subject, subject));
            item.put("pending", pendingBySubject.getOrDefault(subject, 0L));
            item.put("mastered", masteredBySubject.getOrDefault(subject, 0L));
            subjects.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pending", (long) pending.size());
        payload.put("mastered", (long) mastered.size());
        payload.put("total", (long) pending.size() + mastered.size());
        payload.put("subjects", subjects);
        return payload;
    }

    /** 刷题入口汇总：按年 / 按科目 / 未做 / 错题 各自的题量。 */
    public Map<String, Object> entrances(Long userId) {
        List<Map<String, Object>> subjects = new ArrayList<>();
        for (String subject : SUBJECTS) {
            List<Map<String, Object>> rows = questionMapper.queue(
                    userId, null, subject, null, null, false, false, false, false);
            long done = rows.stream().filter(r -> r.get("result") != null).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", subject);
            item.put("name", SUBJECT_NAMES.getOrDefault(subject, subject));
            item.put("total", rows.size());
            item.put("done", done);
            subjects.add(item);
        }

        List<Map<String, Object>> years = new ArrayList<>();
        for (Map<String, Object> row : questionMapper.yearStats()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("year", row.get("year"));
            item.put("total", row.get("total"));
            years.add(item);
        }

        long untouched = questionMapper.queue(userId, null, null, null, null, false, true, false, false).size();
        long wrongPending = userId == null
                ? 0
                : questionMapper.queue(userId, null, null, null, null, false, false, true, false).size();
        long wrongMastered = userId == null
                ? 0
                : questionMapper.queue(userId, null, null, null, null, false, false, false, true).size();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjects", subjects);
        payload.put("years", years);
        payload.put("untouched", untouched);
        payload.put("wrong", wrongPending);
        payload.put("wrongPending", wrongPending);
        payload.put("wrongMastered", wrongMastered);
        return payload;
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
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
}