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
    private final QuestionService questionService;

    public PracticeService(QuestionMapper questionMapper, QuestionService questionService) {
        this.questionMapper = questionMapper;
        this.questionService = questionService;
    }

    /**
     * 刷题队列。scope 决定取全部 / 仅错题 / 仅未做；
     * wrong 与 untouched 需要登录（游客没有作答记录，按未做处理）。
     *
     * @param page 页码，从 1 开始；null 或 0 表示不分页（保持历史行为）
     * @param size 每页条数，仅 page 有效时生效，上限 500
     */
    public Map<String, Object> queue(Long userId, Integer year, String subject,
                                     String blockId, String pointId, String scope,
                                     Integer page, Integer size, boolean latest) {
        // wrong = 所有错过的题（含已订正）；wrong-pending = 待订正；wrong-mastered = 已订正
        boolean onlyWrong = SCOPE_WRONG.equalsIgnoreCase(scope);
        boolean onlyUntouched = SCOPE_UNTOUCHED.equalsIgnoreCase(scope);
        boolean onlyPendingWrong = SCOPE_WRONG_PENDING.equalsIgnoreCase(scope);
        boolean onlyMasteredWrong = SCOPE_WRONG_MASTERED.equalsIgnoreCase(scope);
        String subjectFilter = isEmpty(subject) ? null : subject;
        String blockFilter = isEmpty(blockId) ? null : blockId;
        String pointFilter = isEmpty(pointId) ? null : pointId;

        // 游客看错题本没有意义，直接回空队列而不是回全量
        if ((onlyWrong || onlyPendingWrong || onlyMasteredWrong) && userId == null) {
            return payload(List.of(), year, subject, blockId, pointId, scope,
                    List.of(), 0, 0, page, size);
        }

        // 年份分组**不受 year 过滤**：前端要用它渲染年份导航，并据此决定「默认看哪一年」。
        // 过滤条件里除 year 之外都生效，所以导航显示的是「当前范围下每年有多少题」。
        List<Map<String, Object>> yearRows = questionMapper.queueYearCounts(
                userId, null, subjectFilter, blockFilter, pointFilter,
                onlyWrong, onlyUntouched, onlyPendingWrong, onlyMasteredWrong);
        List<Map<String, Object>> years = new ArrayList<>();
        long totalAll = 0;
        Integer latestYear = null;
        Map<Integer, Map<String, Object>> byYear = new LinkedHashMap<>();
        for (Map<String, Object> row : yearRows) {
            long count = toLong(row.get("total"));
            totalAll += count;
            Integer y = row.get("year") == null ? null : ((Number) row.get("year")).intValue();
            if (y != null && (latestYear == null || y > latestYear)) {
                latestYear = y;
            }
            Map<String, Object> bucket = byYear.get(y);
            if (bucket == null) {
                bucket = new LinkedHashMap<>();
                bucket.put("year", y);
                bucket.put("total", 0L);
                bucket.put("subjects", new LinkedHashMap<String, Long>());
                byYear.put(y, bucket);
                years.add(bucket);
            }
            bucket.put("total", toLong(bucket.get("total")) + count);
            @SuppressWarnings("unchecked")
            Map<String, Long> subjects = (Map<String, Long>) bucket.get("subjects");
            subjects.put(String.valueOf(row.get("subject")), count);
        }

        // 只加载某一年的题：year 显式给出就用它；latest=true 用最新一卷；
        // 两者都没有（旧行为）才返回全量。
        Integer effectiveYear = year;
        if (effectiveYear == null && latest) {
            effectiveYear = latestYear;
        }
        Integer limit = null;
        Integer offset = 0;
        if (page != null && page > 0 && size != null && size > 0) {
            limit = Math.min(size, 500);
            offset = (page - 1) * limit;
        }

        List<Map<String, Object>> rows = questionMapper.queue(
                userId, effectiveYear, subjectFilter, blockFilter, pointFilter,
                onlyWrong, onlyUntouched, onlyPendingWrong, onlyMasteredWrong,
                limit, offset);

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

        long yearTotal = effectiveYear == null
                ? items.size()
                : toLong(byYear.getOrDefault(effectiveYear, Map.of("total", 0L)).get("total"));
        Map<String, Object> payload = payload(items, effectiveYear, subject, blockId, pointId, scope,
                years, totalAll, yearTotal, page, size);

        // 把「首题」一起带回去：刷题页原本要等队列返回、再发一次 /questions/{examRef}，
        // 慢链路上这是两次串行往返（各 0.9–2.7 秒）。首页（或不分页）时首题是确定的，
        // 直接内联可以让首屏少一跳；翻页时仍然按需单题取。
        boolean firstPage = page == null || page <= 1;
        if (firstPage && !items.isEmpty() && !(onlyWrong || onlyPendingWrong || onlyMasteredWrong)) {
            String firstRef = String.valueOf(items.get(0).get("examRef"));
            try {
                payload.put("firstQuestion", questionService.detailOf(firstRef, userId, false));
            } catch (Exception ignored) {
                // 首题取不到就退回前端单独请求，不影响队列本身
            }
        }
        return payload;
    }

    /** 把「年份 × 科目」分组计数再按总数汇总。 */
    private long sumQueueCounts(List<Map<String, Object>> rows) {
        long total = 0;
        for (Map<String, Object> row : rows) {
            total += toLong(row.get("total"));
        }
        return total;
    }
    private Map<String, Object> payload(List<Map<String, Object>> items, Integer year, String subject,
                                        String blockId, String pointId, String scope,
                                        List<Map<String, Object>> years, long total,
                                        long yearTotal, Integer page, Integer size) {
        long answered = items.stream().filter(i -> Boolean.TRUE.equals(i.get("answered"))).count();
        long correct = items.stream().filter(i -> Boolean.TRUE.equals(i.get("correct"))).count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope);
        payload.put("year", year);
        payload.put("subject", subject);
        payload.put("blockId", blockId);
        payload.put("pointId", pointId);
        // total = 当前筛选条件下「所有年份」的总数（与 years 汇总一致）
        payload.put("total", total);
        // yearTotal = 本次实际加载的那一年的题数（前端据此显示「2026 年真题 · 共 47 题」）
        payload.put("yearTotal", yearTotal);
        payload.put("answered", answered);
        payload.put("correct", correct);
        payload.put("page", page);
        payload.put("size", size);
        payload.put("years", years);
        payload.put("items", items);
        return payload;
    }

    /**
     * 错题本：待订正 / 已订正的数量与分科目分布。
     * 待订正 = 曾答错且尚未在重做中答对；已订正 = 重做已答对。
     */
    public Map<String, Object> wrongBook(Long userId) {
        // 只需要「按科目的错题数」，不需要把整行队列读出来再数：用聚合查询各回一条 SQL。
        List<Map<String, Object>> pending = userId == null
                ? List.of()
                : questionMapper.queueYearCounts(userId, null, null, null, null, false, false, true, false);
        List<Map<String, Object>> mastered = userId == null
                ? List.of()
                : questionMapper.queueYearCounts(userId, null, null, null, null, false, false, false, true);

        Map<String, Long> pendingBySubject = new LinkedHashMap<>();
        Map<String, Long> masteredBySubject = new LinkedHashMap<>();
        for (Map<String, Object> row : pending) {
            pendingBySubject.merge(String.valueOf(row.get("subject")), toLong(row.get("total")), Long::sum);
        }
        for (Map<String, Object> row : mastered) {
            masteredBySubject.merge(String.valueOf(row.get("subject")), toLong(row.get("total")), Long::sum);
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

    /**
     * 刷题入口汇总：按年 / 按科目 / 未做 / 错题 各自的题量。
     *
     * <p>原来每个科目都跑一次完整队列查询、再把整行读出来数（4 科 + 未做 + 两类错题 = 7 次全量扫描）。
     * 这里改成全部走聚合查询：入口卡片只要数字，不需要把题目行搬出来。
     */
    public Map<String, Object> entrances(Long userId) {
        // 一次分组查询拿到「科目 × 年份」的题量，再按科目汇总
        Map<String, Long> totalBySubject = new LinkedHashMap<>();
        long totalAll = 0;
        for (Map<String, Object> row : questionMapper.queueYearCounts(
                userId, null, null, null, null, false, false, false, false)) {
            long count = toLong(row.get("total"));
            totalAll += count;
            totalBySubject.merge(String.valueOf(row.get("subject")), count, Long::sum);
        }

        // 已做题数按科目一条聚合即可；null userId（游客）时没有作答记录
        Map<String, Long> doneBySubject = new LinkedHashMap<>();
        long doneAll = 0;
        if (userId != null) {
            for (Map<String, Object> row : questionMapper.attemptedCountBySubject(userId)) {
                long done = toLong(row.get("done"));
                doneAll += done;
                doneBySubject.put(String.valueOf(row.get("subject")), done);
            }
        }

        List<Map<String, Object>> subjects = new ArrayList<>();
        for (String subject : SUBJECTS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", subject);
            item.put("name", SUBJECT_NAMES.getOrDefault(subject, subject));
            item.put("total", totalBySubject.getOrDefault(subject, 0L));
            item.put("done", doneBySubject.getOrDefault(subject, 0L));
            subjects.add(item);
        }

        List<Map<String, Object>> years = new ArrayList<>();
        for (Map<String, Object> row : questionMapper.yearStats()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("year", row.get("year"));
            item.put("total", row.get("total"));
            years.add(item);
        }

        long untouched = Math.max(0, totalAll - doneAll);
        long wrongPending = userId == null ? 0 : sumQueueCounts(
                questionMapper.queueYearCounts(userId, null, null, null, null, false, false, true, false));
        long wrongMastered = userId == null ? 0 : sumQueueCounts(
                questionMapper.queueYearCounts(userId, null, null, null, null, false, false, false, true));

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