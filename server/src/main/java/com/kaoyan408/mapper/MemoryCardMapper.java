package com.kaoyan408.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaoyan408.entity.MemoryCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MemoryCardMapper extends BaseMapper<MemoryCard> {

    /**
     * 到期应复习的卡片（含从未复习过的），并联出知识块与所属知识点。
     * 只取可背诵的块类型：callout（要点）与 formula（公式）。
     */
    @Select("""
            SELECT c.id            AS cardId,
                   c.block_id      AS blockId,
                   c.box           AS box,
                   c.review_count  AS reviewCount,
                   c.correct_count AS correctCount,
                   c.next_review_date AS nextReviewDate,
                   b.type          AS type,
                   b.content_json  AS content,
                   b.subpoint_title AS subpointTitle,
                   p.point_id      AS pointId,
                   p.title         AS pointTitle,
                   p.subject       AS subject
            FROM memory_card c
            JOIN knowledge_block b ON b.block_id = c.block_id
            JOIN knowledge_point p ON p.point_id = b.point_id
            WHERE c.user_id = #{userId}
              AND (c.next_review_date IS NULL OR c.next_review_date <= CURDATE())
            ORDER BY c.next_review_date IS NULL DESC, c.next_review_date ASC, c.review_count ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> dueCards(@Param("userId") Long userId, @Param("limit") int limit);

    /** 卡片总量与掌握分布，用于统计。 */
    @Select("""
            SELECT COUNT(*)                                            AS total,
                   SUM(CASE WHEN c.box >= 5 THEN 1 ELSE 0 END)         AS solid,
                   SUM(CASE WHEN c.next_review_date <= CURDATE()
                             OR c.next_review_date IS NULL THEN 1 ELSE 0 END) AS due,
                   COALESCE(SUM(c.review_count), 0)                     AS reviews,
                   COALESCE(SUM(c.correct_count), 0)                    AS corrects
            FROM memory_card c
            WHERE c.user_id = #{userId}
            """)
    Map<String, Object> cardSummary(@Param("userId") Long userId);

    /** 按科目统计卡片数。 */
    @Select("""
            SELECT p.subject AS subject, COUNT(*) AS total
            FROM memory_card c
            JOIN knowledge_block b ON b.block_id = c.block_id
            JOIN knowledge_point p ON p.point_id = b.point_id
            WHERE c.user_id = #{userId}
            GROUP BY p.subject
            """)
    List<Map<String, Object>> cardsBySubject(@Param("userId") Long userId);

    /** 各记忆盒的卡片数，用于展示记忆分布（盒号 0–7，对应 0/1/2/4/7/15/30/60 天）。 */
    @Select("""
            SELECT c.box AS box, COUNT(*) AS total
            FROM memory_card c
            WHERE c.user_id = #{userId}
            GROUP BY c.box
            ORDER BY c.box
            """)
    List<Map<String, Object>> cardBoxDistribution(@Param("userId") Long userId);

    /**
     * 批量为知识点取「题目数 + 一道代表真题」。
     *
     * 卡片底部用来展示「该知识点在真题里出现过几次、最近一次是哪道」，
     * 让背诵与考试建立联系。按知识点批量查，避免逐卡请求。
     * 年份在 exam_year 表，经 question.exam_id 关联；代表真题取年份最新的一道。
     */
    @Select("""
            <script>
            SELECT kb.point_id AS pointId,
                   COUNT(DISTINCT qk.question_id) AS questionCount,
                   SUBSTRING_INDEX(
                     GROUP_CONCAT(
                       CONCAT(ey.year, '|', q.exam_ref, '|', q.exam_number)
                       ORDER BY ey.year DESC, q.exam_number DESC SEPARATOR ';'
                     ),
                     ';', 1
                   ) AS latest
            FROM knowledge_block kb
            JOIN question_knowledge qk ON qk.block_id = kb.block_id
            JOIN question q ON q.id = qk.question_id
            JOIN exam_year ey ON ey.id = q.exam_id
            WHERE kb.point_id IN
              <foreach collection="pointIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY kb.point_id
            </script>
            """)
    List<Map<String, Object>> pointQuestionContext(@Param("pointIds") List<String> pointIds);

    /**
     * 尚未加入背诵、但适合做成卡片的块（callout / formula）。
     * 用于「一键添加」时按知识点批量加入。
     */
    @Select("""
            <script>
            SELECT b.block_id AS blockId, b.type AS type,
                   b.subpoint_title AS subpointTitle, p.title AS pointTitle
            FROM knowledge_block b
            JOIN knowledge_point p ON p.point_id = b.point_id
            WHERE b.type IN ('callout', 'formula')
              AND NOT EXISTS (
                SELECT 1 FROM memory_card c
                WHERE c.user_id = #{userId} AND c.block_id = b.block_id
              )
            <if test="pointId != null and pointId != ''">AND b.point_id = #{pointId}</if>
            <if test="subject != null and subject != ''">AND p.subject = #{subject}</if>
            ORDER BY p.sort_order, b.block_order
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> addableBlocks(@Param("userId") Long userId,
                                            @Param("pointId") String pointId,
                                            @Param("subject") String subject,
                                            @Param("limit") int limit);
}