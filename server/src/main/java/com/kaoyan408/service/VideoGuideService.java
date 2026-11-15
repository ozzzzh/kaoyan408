package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.KnowledgeBlock;
import com.kaoyan408.entity.KnowledgePoint;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionKnowledge;
import com.kaoyan408.mapper.KnowledgeBlockMapper;
import com.kaoyan408.mapper.KnowledgePointMapper;
import com.kaoyan408.mapper.QuestionKnowledgeMapper;
import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 视频指引：按题目关联的知识点，生成到 B 站的搜索入口。
 *
 * <p>为什么不是「直接给播放链接」：本项目不托管、也不转存任何第三方课程内容。
 * 题目与知识点的对应关系是本项目自有数据，据此给出「该去搜什么」既能让用户
 * 一步找到对应课程，也不涉及版权与链接失效问题。
 *
 * <p>搜索词构成：老师名 + 科目 + 知识点标题。只给知识点的话，搜「线性表」会得到
 * 大量无关结果；加上科目与老师名能显著提高命中率。
 */
@Service
public class VideoGuideService {

    /** 408 常见授课老师/机构，顺序即展示顺序。 */
    private static final List<String> TEACHERS = List.of("王道", "里昂", "复旦");

    private static final Map<String, String> SUBJECT_NAMES = Map.of(
            "ds", "数据结构",
            "os", "操作系统",
            "co", "计算机组成原理",
            "cn", "计算机网络");

    private static final String BILIBILI_SEARCH = "https://search.bilibili.com/all?keyword=";

    private final QuestionMapper questionMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final KnowledgeBlockMapper blockMapper;
    private final KnowledgePointMapper pointMapper;

    public VideoGuideService(QuestionMapper questionMapper,
                             QuestionKnowledgeMapper questionKnowledgeMapper,
                             KnowledgeBlockMapper blockMapper,
                             KnowledgePointMapper pointMapper) {
        this.questionMapper = questionMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.blockMapper = blockMapper;
        this.pointMapper = pointMapper;
    }

    /**
     * 某道题的视频指引。
     *
     * <p>返回结构：
     * <pre>
     * {
     *   "examRef": "exam-2026-1",
     *   "subjectName": "数据结构",
     *   "chapterName": "线性表",
     *   "points": [{ "title": "线性表的基本操作", "subpointTitle": "顺序表的插入" }],
     *   "sources": [{ "teacher": "王道", "keyword": "...", "url": "https://..." }]
     * }
     * </pre>
     */
    public Map<String, Object> guideOf(String examRef) {
        Question question = questionMapper.selectOne(
                new QueryWrapper<Question>().eq("exam_ref", examRef).last("LIMIT 1"));
        if (question == null) {
            throw new BusinessException(404, "未找到该题目：" + examRef);
        }

        String subjectName = SUBJECT_NAMES.getOrDefault(question.getSubject(), "");

        // 该题关联的知识块 → 知识点标题（去重保序）
        List<String> blockIds = new ArrayList<>();
        for (QuestionKnowledge link : questionKnowledgeMapper.selectList(
                new QueryWrapper<QuestionKnowledge>().eq("question_id", question.getId()))) {
            blockIds.add(link.getBlockId());
        }

        List<Map<String, Object>> points = new ArrayList<>();
        Set<String> pointTitles = new LinkedHashSet<>();

        if (!blockIds.isEmpty()) {
            List<KnowledgeBlock> blocks = blockMapper.selectList(
                    new QueryWrapper<KnowledgeBlock>().in("block_id", blockIds));
            List<String> pointIds = blocks.stream()
                    .map(KnowledgeBlock::getPointId).filter(Objects::nonNull).distinct().toList();

            Map<String, String> titleById = new LinkedHashMap<>();
            if (!pointIds.isEmpty()) {
                for (KnowledgePoint point : pointMapper.selectList(
                        new QueryWrapper<KnowledgePoint>().in("point_id", pointIds))) {
                    titleById.put(point.getPointId(), point.getTitle());
                }
            }

            for (KnowledgeBlock block : blocks) {
                String title = titleById.get(block.getPointId());
                if (title != null && pointTitles.add(title)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", title);
                    item.put("subpointTitle", block.getSubpointTitle());
                    points.add(item);
                }
            }
        }

        // 搜索词优先用最具体的知识点；没有关联时退回题目的章节名
        String focus = pointTitles.stream().findFirst()
                .orElseGet(() -> question.getChapterName() != null
                        ? question.getChapterName() : subjectName);

        List<Map<String, Object>> sources = new ArrayList<>();
        for (String teacher : TEACHERS) {
            sources.add(buildSource(teacher, teacher + " " + subjectName, focus));
        }
        // 不限定老师的通用搜索，命中面更宽
        sources.add(buildSource("综合", subjectName, focus));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("examRef", examRef);
        payload.put("subjectName", subjectName);
        payload.put("chapterName", question.getChapterName());
        payload.put("points", points);
        payload.put("sources", sources);
        return payload;
    }

    private Map<String, Object> buildSource(String label, String prefix, String focus) {
        String keyword = (prefix + " " + focus).trim();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("teacher", label);
        item.put("keyword", keyword);
        item.put("url", BILIBILI_SEARCH + encode(keyword));
        return item;
    }

    /**
     * URL 编码。
     *
     * <p>用 {@link URLEncoder} 会把空格编成 {@code +}（这是 form 编码规则）。
     * 查询串里 B 站能识别，但它属于 application/x-www-form-urlencoded 的约定，
     * 放进 URL 不够标准；统一换成 {@code %20}，语义明确、也不会被别的解析器误会。
     */
    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}