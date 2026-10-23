package com.kaoyan408.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaoyan408.entity.Question;
import com.kaoyan408.core.vo.SubjectStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /** 各科题量与当前用户的完成情况。userId 为 null 时只统计题量。 */
    @Select("""
            SELECT q.subject              AS subject,
                   COUNT(q.id)            AS total,
                   COUNT(a.id)            AS done,
                   COALESCE(SUM(a.result), 0) AS correct
            FROM question q
            LEFT JOIN question_attempt a ON a.question_id = q.id AND a.user_id = #{userId}
            GROUP BY q.subject
            """)
    List<SubjectStats> subjectStats(@Param("userId") Long userId);

    /** 按年份统计题量。 */
    @Select("""
            SELECT e.year AS year, COUNT(q.id) AS total
            FROM exam_year e
            LEFT JOIN question q ON q.exam_id = e.id
            GROUP BY e.year
            ORDER BY e.year
            """)
    List<Map<String, Object>> yearStats();

    /** 每个知识块的题目总数（含全部年份）。 */
    @Select("""
            SELECT block_id AS blockId, COUNT(*) AS total
            FROM question_knowledge
            GROUP BY block_id
            """)
    List<Map<String, Object>> blockQuestionTotals();

    /** 用户在每个知识块上的做题情况。 */
    @Select("""
            SELECT qk.block_id AS blockId,
                   COUNT(a.id)          AS done,
                   COALESCE(SUM(a.result), 0) AS correct
            FROM question_knowledge qk
            JOIN question_attempt a ON a.question_id = qk.question_id
            WHERE a.user_id = #{userId}
            GROUP BY qk.block_id
            """)
    List<Map<String, Object>> blockProgressOfUser(@Param("userId") Long userId);
}