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

    /**
     * 各科掌握度分桶（对齐 408os 的「掌握 / 不熟 / 不会 / 未做」）。
     * 掌握：答对且未用提示；不熟：答对但用过提示；不会：答错；未做：无作答记录。
     */
    @Select("""
            SELECT q.subject AS subject,
                   SUM(CASE WHEN a.id IS NOT NULL AND a.result = 1 AND COALESCE(a.hints_used, 0) = 0 THEN 1 ELSE 0 END) AS mastered,
                   SUM(CASE WHEN a.id IS NOT NULL AND a.result = 1 AND COALESCE(a.hints_used, 0) > 0 THEN 1 ELSE 0 END) AS shaky,
                   SUM(CASE WHEN a.id IS NOT NULL AND a.result = 0 THEN 1 ELSE 0 END) AS failed,
                   SUM(CASE WHEN a.id IS NULL THEN 1 ELSE 0 END) AS untouched
            FROM question q
            LEFT JOIN question_attempt a ON a.question_id = q.id AND a.user_id = #{userId}
            GROUP BY q.subject
            """)
    List<Map<String, Object>> masteryBySubject(@Param("userId") Long userId);

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

    /**
     * 知识点级掌握情况：按知识点聚合其下所有块的做题数据。
     *
     * <p>题量按 COUNT(DISTINCT question_id) 去重：一道题可能挂在同一知识点的多个块上，
     * 若直接对块计数会把同一题重复计入（实测 co-vm-impl 求和 468 / 去重仅 61）。
     */
    @Select("""
            <script>
            SELECT p.point_id   AS pointId,
                   p.title      AS title,
                   p.subject    AS subject,
                   COUNT(DISTINCT q.id)                                   AS total,
                   COUNT(DISTINCT CASE WHEN a.id IS NOT NULL THEN q.id END) AS done,
                   COUNT(DISTINCT CASE WHEN a.result = 1 THEN q.id END)     AS correct
            FROM knowledge_point p
            JOIN knowledge_block b ON b.point_id = p.point_id
            JOIN question_knowledge qk ON qk.block_id = b.block_id
            JOIN question q ON q.id = qk.question_id
            LEFT JOIN question_attempt a ON a.question_id = q.id AND a.user_id = #{userId}
            <where>
              <if test="subject != null and subject != ''">AND p.subject = #{subject}</if>
            </where>
            GROUP BY p.point_id, p.title, p.subject
            ORDER BY p.sort_order ASC
            </script>
            """)
    List<Map<String, Object>> pointMastery(@Param("userId") Long userId,
                                           @Param("subject") String subject);

    /**
     * 刷题队列：按条件筛出题目，并带出当前用户的作答状态。
     * 只返回渲染列表与导航所需的轻量字段（不下发题干与解析）。
     *
     * <p>过滤条件全部可选：year / subject / blockId / pointId /
     * onlyWrong（仅错题）/ onlyUntouched（仅未做）。
     *
     * <p>pointId 必须经 knowledge_block.point_id 关联，
     * 不能按 block_id 前缀猜测：块的命名（如 kb-crc-basics-1）与知识点 id（kp-crc）无前缀关系。
     */
    @Select("""
            <script>
            SELECT q.id            AS id,
                   q.exam_ref      AS examRef,
                   q.exam_number   AS number,
                   q.subject       AS subject,
                   q.chapter_name  AS chapterName,
                   q.question_type AS questionType,
                   q.score         AS score,
                   q.difficulty    AS difficulty,
                   e.year          AS year,
                   a.result        AS result,
                   a.wrong_count   AS wrongCount,
                   a.retry_correct_count AS retryCorrectCount,
                   a.mastered_at   AS masteredAt,
                   CASE WHEN a.id IS NULL THEN 1 ELSE 0 END AS untouched
            FROM question q
            JOIN exam_year e ON e.id = q.exam_id
            LEFT JOIN question_attempt a ON a.question_id = q.id AND a.user_id = #{userId}
            <if test="blockId != null or pointId != null">
              JOIN question_knowledge qk ON qk.question_id = q.id
            </if>
            <if test="pointId != null">
              JOIN knowledge_block kb ON kb.block_id = qk.block_id AND kb.point_id = #{pointId}
            </if>
            <where>
              <if test="year != null">AND e.year = #{year}</if>
              <if test="subject != null and subject != ''">AND q.subject = #{subject}</if>
              <if test="blockId != null and blockId != ''">AND qk.block_id = #{blockId}</if>
              <if test="onlyWrong">AND a.wrong_count &gt; 0</if>
              <if test="onlyUntouched">AND a.id IS NULL</if>
              <if test="onlyPendingWrong">AND a.wrong_count &gt; 0 AND a.mastered_at IS NULL</if>
              <if test="onlyMasteredWrong">AND a.wrong_count &gt; 0 AND a.mastered_at IS NOT NULL</if>
            </where>
            GROUP BY q.id, q.exam_ref, q.exam_number, q.subject, q.chapter_name,
                     q.question_type, q.score, q.difficulty, e.year, a.result, untouched,
                     a.wrong_count, a.retry_correct_count, a.mastered_at, a.first_wrong_at
            ORDER BY
              <if test="onlyPendingWrong">a.first_wrong_at ASC,</if>
              e.year DESC, q.exam_number ASC
            </script>
            """)
    List<Map<String, Object>> queue(@Param("userId") Long userId,
                                    @Param("year") Integer year,
                                    @Param("subject") String subject,
                                    @Param("blockId") String blockId,
                                    @Param("pointId") String pointId,
                                    @Param("onlyWrong") boolean onlyWrong,
                                    @Param("onlyUntouched") boolean onlyUntouched,
                                    @Param("onlyPendingWrong") boolean onlyPendingWrong,
                                    @Param("onlyMasteredWrong") boolean onlyMasteredWrong);

    /** 某个知识块的关联真题（轻量字段，用于正文页的「本节关联 N 道真题」）。 */
    @Select("""
            SELECT q.exam_ref AS examRef, q.exam_number AS number, e.year AS year,
                   q.subject AS subject, q.question_type AS questionType
            FROM question_knowledge qk
            JOIN question q ON q.id = qk.question_id
            JOIN exam_year e ON e.id = q.exam_id
            WHERE qk.block_id = #{blockId}
            ORDER BY e.year DESC, q.exam_number ASC
            """)
    List<Map<String, Object>> questionsOfBlock(@Param("blockId") String blockId);

    /** 一批知识块的关联真题去重计数，避免 N+1 查询。 */
    @Select("""
            <script>
            SELECT qk.block_id AS blockId,
                   COUNT(DISTINCT q.id) AS total
            FROM question_knowledge qk
            JOIN question q ON q.id = qk.question_id
            WHERE qk.block_id IN
            <foreach item="id" collection="blockIds" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY qk.block_id
            </script>
            """)
    List<Map<String, Object>> questionCountsOfBlocks(@Param("blockIds") List<String> blockIds);

    /**
     * 某知识点关联的真题**去重**总数。
     *
     * <p>不能把各块的关联数相加：一道题可能同时挂在同一知识点的多个块上
     * （实测 co-vm-impl 求和 468 / 去重仅 61），相加会严重虚高。
     * 也不能按 block_id 前缀猜测归属：块 id（kb-crc-basics-1）与知识点 id（kp-crc）无前缀关系，
     * 必须经 knowledge_block.point_id 关联。
     */
    @Select("""
            SELECT COUNT(DISTINCT qk.question_id) AS total
            FROM knowledge_block kb
            JOIN question_knowledge qk ON qk.block_id = kb.block_id
            WHERE kb.point_id = #{pointId}
            """)
    long distinctQuestionsOfPoint(@Param("pointId") String pointId);
}