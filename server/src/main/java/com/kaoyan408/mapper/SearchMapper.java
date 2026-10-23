package com.kaoyan408.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 搜索查询。
 *
 * <p>相关性用 CASE WHEN 算成分数再排序，模拟「标题精确 &gt; 前缀 &gt; 包含 &gt; 正文」的权重，
 * 避免纯 LIKE 结果顺序随意。
 */
@Mapper
public interface SearchMapper {

    /**
     * 搜索知识点：标题权重最高，其次正文（经知识块）。
     *
     * <p>正文匹配排除 image / html 块：这两类块内嵌 SVG 与 base64 数据，
     * 关键词会随机命中编码串，产生与主题无关的结果（实测「KMP」曾命中
     * 虚拟存储器章节的内嵌 SVG）。
     */
    @Select("""
            SELECT p.point_id  AS pointId,
                   p.title     AS title,
                   p.subject   AS subject,
                   (SELECT COUNT(*) FROM knowledge_block b WHERE b.point_id = p.point_id) AS blockCount,
                   MAX(CASE
                         WHEN p.title = #{keyword} THEN 100
                         WHEN p.title LIKE CONCAT(#{keyword}, '%') THEN 80
                         WHEN p.title LIKE CONCAT('%', #{keyword}, '%') THEN 60
                         ELSE 10
                       END) AS score,
                   (SELECT COUNT(*) FROM knowledge_block b2
                     WHERE b2.point_id = p.point_id
                       AND b2.type NOT IN ('image', 'html')
                       AND b2.content_json LIKE CONCAT('%', #{keyword}, '%')) AS bodyHits
            FROM knowledge_point p
            LEFT JOIN knowledge_block b
                   ON b.point_id = p.point_id
                  AND b.type NOT IN ('image', 'html')
            WHERE p.title LIKE CONCAT('%', #{keyword}, '%')
               OR b.content_json LIKE CONCAT('%', #{keyword}, '%')
            GROUP BY p.point_id, p.title, p.subject
            ORDER BY score DESC, bodyHits DESC, p.sort_order ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> searchPoints(@Param("keyword") String keyword,
                                           @Param("limit") int limit);

    /**
     * 搜索题目：题干、章节名、题号、标签。
     * title 供列表展示（含年份与题号），hint 为科目与章节。
     */
    @Select("""
            SELECT q.exam_ref      AS examRef,
                   q.exam_number   AS number,
                   q.subject       AS subject,
                   q.chapter_name  AS chapterName,
                   q.question_type AS questionType,
                   q.score         AS score,
                   e.year          AS year,
                   CONCAT(e.year, ' 年第 ', q.exam_number, ' 题') AS title,
                   CONCAT(
                     CASE q.subject
                       WHEN 'ds' THEN '数据结构'
                       WHEN 'os' THEN '操作系统'
                       WHEN 'co' THEN '计算机组成原理'
                       WHEN 'cn' THEN '计算机网络'
                       ELSE q.subject END,
                     IFNULL(CONCAT(' · ', q.chapter_name), '')
                   ) AS hint,
                   LEFT(REPLACE(REPLACE(q.stem, '\\n', ' '), '\\r', ' '), 120) AS snippet,
                   (CASE
                      WHEN q.exam_ref = #{keyword} THEN 100
                      WHEN q.chapter_name = #{keyword} THEN 80
                      WHEN q.chapter_name LIKE CONCAT(#{keyword}, '%') THEN 70
                      WHEN q.stem LIKE CONCAT('%', #{keyword}, '%') THEN 50
                      WHEN IFNULL(q.tags_json, '') LIKE CONCAT('%', #{keyword}, '%') THEN 40
                      ELSE 10
                    END) AS score
            FROM question q
            JOIN exam_year e ON e.id = q.exam_id
            WHERE q.stem LIKE CONCAT('%', #{keyword}, '%')
               OR q.chapter_name LIKE CONCAT('%', #{keyword}, '%')
               OR IFNULL(q.tags_json, '') LIKE CONCAT('%', #{keyword}, '%')
               OR q.exam_ref = #{keyword}
            ORDER BY score DESC, e.year DESC, q.exam_number ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> searchQuestions(@Param("keyword") String keyword,
                                              @Param("limit") int limit);

    /** 按年份 + 题号精确定位（题号查询走这条，避免被题干关键词干扰）。 */
    @Select("""
            SELECT q.exam_ref      AS examRef,
                   q.exam_number   AS number,
                   q.subject       AS subject,
                   q.chapter_name  AS chapterName,
                   q.question_type AS questionType,
                   q.score         AS score,
                   e.year          AS year,
                   CONCAT(e.year, ' 年第 ', q.exam_number, ' 题') AS title,
                   CONCAT(
                     CASE q.subject
                       WHEN 'ds' THEN '数据结构'
                       WHEN 'os' THEN '操作系统'
                       WHEN 'co' THEN '计算机组成原理'
                       WHEN 'cn' THEN '计算机网络'
                       ELSE q.subject END,
                     IFNULL(CONCAT(' · ', q.chapter_name), '')
                   ) AS hint,
                   LEFT(REPLACE(REPLACE(q.stem, '\\n', ' '), '\\r', ' '), 120) AS snippet,
                   100 AS score
            FROM question q
            JOIN exam_year e ON e.id = q.exam_id
            WHERE e.year = #{year} AND q.exam_number = #{number}
            LIMIT 5
            """)
    List<Map<String, Object>> questionByYearAndNumber(@Param("year") int year,
                                                      @Param("number") int number);

    /**
     * 搜索知识块正文：用于「正文片段」结果，点击可跳到所属知识点。
     * 同样排除 image / html 块，避免命中内嵌 SVG 与 base64 数据。
     */
    @Select("""
            SELECT b.block_id     AS blockId,
                   b.point_id     AS pointId,
                   b.subpoint_title AS subpointTitle,
                   b.type         AS type,
                   b.content_json AS text,
                   p.title        AS pointTitle,
                   p.subject      AS subject,
                   (CASE
                      WHEN b.type = 'paragraph' THEN 30
                      WHEN b.type = 'formula' THEN 25
                      WHEN b.type = 'callout' THEN 20
                      ELSE 10
                    END
                    + CASE WHEN IFNULL(b.subpoint_title, '') LIKE CONCAT('%', #{keyword}, '%') THEN 20 ELSE 0 END
                    + CASE WHEN p.title LIKE CONCAT('%', #{keyword}, '%') THEN 40 ELSE 0 END
                   ) AS score
            FROM knowledge_block b
            JOIN knowledge_point p ON p.point_id = b.point_id
            WHERE b.type NOT IN ('image', 'html')
              AND (b.content_json LIKE CONCAT('%', #{keyword}, '%')
                   OR IFNULL(b.subpoint_title, '') LIKE CONCAT('%', #{keyword}, '%')
                   OR p.title LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY score DESC, b.block_order ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> searchBlocks(@Param("keyword") String keyword,
                                           @Param("limit") int limit);
}