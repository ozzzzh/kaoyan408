package com.kaoyan408.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaoyan408.entity.MockSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MockSessionMapper extends BaseMapper<MockSession> {

    /**
     * 历史成绩：已交卷的场次，带当年卷面满分用于换算正确率。
     */
    @Select("""
            SELECT s.id            AS sessionId,
                   s.exam_year     AS examYear,
                   s.started_at    AS startedAt,
                   s.submitted_at  AS submittedAt,
                   s.duration_minutes AS durationMinutes,
                   s.auto_score    AS autoScore,
                   s.self_score    AS selfScore,
                   s.timed_out     AS timedOut,
                   (SELECT COUNT(*) FROM mock_answer a
                     WHERE a.session_id = s.id AND a.correct = 1) AS correctCount,
                   (SELECT COUNT(*) FROM mock_answer a
                     WHERE a.session_id = s.id AND a.correct IS NOT NULL) AS answeredChoice
            FROM mock_session s
            WHERE s.user_id = #{userId} AND s.status = 'submitted'
            ORDER BY s.submitted_at DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> history(@Param("userId") Long userId, @Param("limit") int limit);

    /** 已交卷的模考记录，用于按年份聚合最好分/最近一次。 */
    @Select("""
            SELECT exam_year AS examYear,
                   (auto_score + self_score) AS totalScore,
                   submitted_at AS submittedAt,
                   duration_minutes AS durationMinutes
            FROM mock_session
            WHERE user_id = #{userId} AND status = 'submitted' AND submitted_at IS NOT NULL
            """)
    List<Map<String, Object>> submittedByYear(@Param("userId") Long userId);
}