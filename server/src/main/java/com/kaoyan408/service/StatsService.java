package com.kaoyan408.service;

import com.kaoyan408.core.vo.SubjectStats;
import com.kaoyan408.entity.DailyStat;
import com.kaoyan408.mapper.DailyStatMapper;
import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习看板数据。未登录时只回题库总量，用于首页展示。
 */
@Service
public class StatsService {

    private static final String[] SUBJECTS = {"ds", "os", "co", "cn"};

    private final QuestionMapper questionMapper;
    private final DailyStatMapper dailyStatMapper;

    public StatsService(QuestionMapper questionMapper, DailyStatMapper dailyStatMapper) {
        this.questionMapper = questionMapper;
        this.dailyStatMapper = dailyStatMapper;
    }

    public Map<String, Object> dashboard(Long userId) {
        List<SubjectStats> rows = questionMapper.subjectStats(userId);
        Map<String, SubjectStats> bySubject = new LinkedHashMap<>();
        for (SubjectStats row : rows) {
            bySubject.put(row.getSubject(), row);
        }

        long totalQuestions = 0;
        long totalDone = 0;
        long totalCorrect = 0;
        List<Map<String, Object>> subjects = new ArrayList<>();
        for (String subject : SUBJECTS) {
            SubjectStats row = bySubject.get(subject);
            long total = row == null || row.getTotal() == null ? 0 : row.getTotal();
            long done = row == null || row.getDone() == null ? 0 : row.getDone();
            long correct = row == null || row.getCorrect() == null ? 0 : row.getCorrect();
            totalQuestions += total;
            totalDone += done;
            totalCorrect += correct;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", subject);
            item.put("total", total);
            item.put("done", done);
            item.put("correct", correct);
            item.put("accuracy", done == 0 ? 0.0 : round(correct * 100.0 / done));
            item.put("progress", total == 0 ? 0.0 : round(done * 100.0 / total));
            subjects.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalQuestions", totalQuestions);
        payload.put("doneQuestions", totalDone);
        payload.put("correctQuestions", totalCorrect);
        payload.put("accuracy", totalDone == 0 ? 0.0 : round(totalCorrect * 100.0 / totalDone));
        payload.put("progress", totalQuestions == 0 ? 0.0 : round(totalDone * 100.0 / totalQuestions));
        payload.put("subjects", subjects);
        payload.put("streakDays", streakDays(userId));
        payload.put("recent", recentDays(userId));
        return payload;
    }

    /** 连续打卡天数：从今天（或昨天）往前数，遇到空档即停。 */
    public int streakDays(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<DailyStat> recent = dailyStatMapper.recent(userId, 120);
        LocalDate cursor = LocalDate.now();
        boolean started = false;
        int streak = 0;
        for (DailyStat stat : recent) {
            LocalDate date = stat.getStatDate();
            if (!started) {
                // 今天没做题时，允许从昨天开始计算
                if (date.equals(cursor)) {
                    started = true;
                } else if (date.equals(cursor.minusDays(1))) {
                    started = true;
                    cursor = cursor.minusDays(1);
                } else {
                    return 0;
                }
            }
            if (!date.equals(cursor)) {
                break;
            }
            if (stat.getQuestions() != null && stat.getQuestions() > 0) {
                streak++;
            }
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 近 14 天做题量，缺失日期补 0，便于前端直接画图。 */
    public List<Map<String, Object>> recentDays(Long userId) {
        Map<LocalDate, DailyStat> byDate = new LinkedHashMap<>();
        if (userId != null) {
            for (DailyStat stat : dailyStatMapper.recent(userId, 14)) {
                byDate.put(stat.getStatDate(), stat);
            }
        }
        List<Map<String, Object>> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int offset = 13; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            DailyStat stat = byDate.get(date);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("questions", stat == null || stat.getQuestions() == null ? 0 : stat.getQuestions());
            item.put("correct", stat == null || stat.getCorrect() == null ? 0 : stat.getCorrect());
            days.add(item);
        }
        return days;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}