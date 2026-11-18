package com.kaoyan408.service;

import com.kaoyan408.mapper.MockSessionMapper;
import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真题卷进度聚合。
 *
 * <p>两种口径分开统计，刻意不合并：
 * <ul>
 *   <li>练习：question_attempt（随手刷题、可反复做）</li>
 *   <li>模考：mock_session（限时整卷、含综合题自评）</li>
 * </ul>
 * 混在一起会让"正确率/完成度"失真——一次 180 分钟的考试和随手做两题不是一件事。
 */
@Service
public class ExamProgressService {

    private final QuestionMapper questionMapper;
    private final MockSessionMapper mockSessionMapper;

    public ExamProgressService(QuestionMapper questionMapper, MockSessionMapper mockSessionMapper) {
        this.questionMapper = questionMapper;
        this.mockSessionMapper = mockSessionMapper;
    }

    /** 返回每卷的 { practice, mock } 两段进度；未登录时 practice 全 0、mock 为 null。 */
    public List<Map<String, Object>> progressOf(Long userId) {
        // 练习口径
        Map<Integer, Map<String, Object>> byYear = new LinkedHashMap<>();
        if (userId != null) {
            for (Map<String, Object> row : questionMapper.practiceByExamYear(userId)) {
                int year = toInt(row.get("examYear"));
                Map<String, Object> practice = new LinkedHashMap<>();
                practice.put("total", toLong(row.get("total")));
                practice.put("done", toLong(row.get("done")));
                practice.put("correct", toLong(row.get("correct")));
                Map<String, Object> item = byYear.computeIfAbsent(year, y -> new LinkedHashMap<>());
                item.put("year", year);
                item.put("practice", practice);
            }
            // 模考口径：同一卷可能多次，取最好分与最近一次
            for (Map<String, Object> row : mockSessionMapper.submittedByYear(userId)) {
                int year = toInt(row.get("examYear"));
                int totalScore = toInt(row.get("totalScore"));
                LocalDateTime submittedAt = row.get("submittedAt") instanceof LocalDateTime t ? t : null;
                Map<String, Object> item = byYear.computeIfAbsent(year, y -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("year", y);
                    return created;
                });
                @SuppressWarnings("unchecked")
                Map<String, Object> mock = (Map<String, Object>) item.get("mock");
                if (mock == null) {
                    mock = new LinkedHashMap<>();
                    mock.put("attempts", 0L);
                    mock.put("bestScore", 0);
                    mock.put("lastScore", 0);
                    mock.put("lastAt", null);
                    mock.put("durationMinutes", toInt(row.get("durationMinutes")));
                    item.put("mock", mock);
                }
                mock.put("attempts", ((Number) mock.get("attempts")).longValue() + 1);
                mock.put("bestScore", Math.max(toInt(mock.get("bestScore")), totalScore));
                Object lastAt = mock.get("lastAt");
                if (lastAt == null || (submittedAt != null && submittedAt.isAfter((LocalDateTime) lastAt))) {
                    mock.put("lastAt", submittedAt);
                    mock.put("lastScore", totalScore);
                    mock.put("durationMinutes", toInt(row.get("durationMinutes")));
                }
            }
        }
        return byYear.values().stream().toList();
    }

    private int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}