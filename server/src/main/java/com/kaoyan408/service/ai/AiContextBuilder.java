package com.kaoyan408.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.KnowledgeBlock;
import com.kaoyan408.entity.KnowledgePoint;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionAttempt;
import com.kaoyan408.entity.QuestionKnowledge;
import com.kaoyan408.mapper.KnowledgeBlockMapper;
import com.kaoyan408.mapper.KnowledgePointMapper;
import com.kaoyan408.mapper.QuestionAttemptMapper;
import com.kaoyan408.mapper.QuestionKnowledgeMapper;
import com.kaoyan408.mapper.QuestionMapper;
import com.kaoyan408.service.StatsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把用户的学习数据压成一段给模型看的摘要。
 *
 * <p>为什么不直接把 dashboard 的 JSON 塞进去：那份数据有十几层嵌套、
 * 热力图 90 天的数组，光序列化就上千 token，模型也读不出重点。
 * 这里只挑三样真正影响复习建议的：四科掌握度、薄弱知识点、最近错题。
 *
 * <p>未登录时返回 null，提示词里就不会出现「这位用户的学习情况」那一段。
 */
@Component
public class AiContextBuilder {

    /** 薄弱知识点最多列几个，多了模型反而会挑花眼。 */
    private static final int MAX_WEAK_POINTS = 8;

    /** 最近错题最多列几道。 */
    private static final int MAX_WRONG = 6;

    private final StatsService statsService;
    private final QuestionAttemptMapper attemptMapper;
    private final QuestionMapper questionMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final KnowledgePointMapper pointMapper;
    private final KnowledgeBlockMapper blockMapper;

    public AiContextBuilder(StatsService statsService,
                            QuestionAttemptMapper attemptMapper,
                            QuestionMapper questionMapper,
                            QuestionKnowledgeMapper questionKnowledgeMapper,
                            KnowledgePointMapper pointMapper,
                            KnowledgeBlockMapper blockMapper) {
        this.statsService = statsService;
        this.attemptMapper = attemptMapper;
        this.questionMapper = questionMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.pointMapper = pointMapper;
        this.blockMapper = blockMapper;
    }

    public String build(Long userId) {
        if (userId == null) {
            return null;
        }
        Map<String, Object> dash = statsService.dashboard(userId);
        if (dash == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(1024);

        // 一、总体
        int total = num(dash.get("totalQuestions"));
        int done = num(dash.get("doneQuestions"));
        int correct = num(dash.get("correctQuestions"));
        sb.append("总题库 ").append(total).append(" 题，已作答 ").append(done).append(" 题");
        if (done > 0) {
            sb.append("，正确率 ").append(Math.round(num(dash.get("accuracy")) * 100)).append('%');
        } else {
            sb.append("（还没开始做题）");
        }
        sb.append("。\n");

        // 二、四科掌握度
        Object subjectsObj = dash.get("subjects");
        if (subjectsObj instanceof List<?> subjects && !subjects.isEmpty()) {
            sb.append("四科情况：\n");
            for (Object o : subjects) {
                if (!(o instanceof Map<?, ?> s)) continue;
                String key = String.valueOf(s.get("subject"));
                sb.append("- ").append(subjectName(key))
                        .append("：共 ").append(s.get("total")).append(" 题，")
                        .append("已做 ").append(s.get("done")).append(" 题，")
                        .append("掌握率 ").append(pct(s.get("masteryRate"))).append("，")
                        .append("答错 ").append(s.get("failed")).append(" 题\n");
            }
        }

        // 三、薄弱知识点：按知识点聚合错题数
        List<String> weak = weakPoints(userId);
        if (!weak.isEmpty()) {
            sb.append("应该优先补的知识点（按答错次数排）：\n");
            for (String w : weak) {
                sb.append("- ").append(w).append('\n');
            }
        }

        // 四、最近错题
        List<String> wrong = recentWrong(userId);
        if (!wrong.isEmpty()) {
            sb.append("最近答错的题：\n");
            for (String w : wrong) {
                sb.append("- ").append(w).append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * 按知识点统计答错次数，取前 N 个。
     *
     * <p>关联链是 question → question_knowledge.block_id → knowledge_block.point_id。
     * 不能直接把 block_id 当知识点 id 用：block 是知识点下的一个知识块，
     * 一个知识点通常有十几个 block，直接拿 block_id 去查知识点会全部落空。
     */
    private List<String> weakPoints(Long userId) {
        List<QuestionAttempt> attempts = attemptMapper.selectList(
                new QueryWrapper<QuestionAttempt>()
                        .eq("user_id", userId)
                        .gt("wrong_count", 0)
                        .orderByDesc("wrong_count")
                        .last("LIMIT 60"));
        if (attempts.isEmpty()) {
            return List.of();
        }

        // questionId -> 答错次数
        Map<Long, Integer> wrongByQuestion = new java.util.HashMap<>();
        for (QuestionAttempt a : attempts) {
            wrongByQuestion.put(a.getQuestionId(),
                    a.getWrongCount() == null ? 1 : a.getWrongCount());
        }

        List<QuestionKnowledge> links = questionKnowledgeMapper.selectList(
                new QueryWrapper<QuestionKnowledge>().in("question_id", wrongByQuestion.keySet()));
        if (links.isEmpty()) {
            return List.of();
        }

        // block_id -> point_id
        List<String> blockIds = links.stream().map(QuestionKnowledge::getBlockId)
                .distinct().toList();
        Map<String, String> pointOfBlock = new java.util.HashMap<>();
        for (KnowledgeBlock block : blockMapper.selectList(
                new QueryWrapper<KnowledgeBlock>().in("block_id", blockIds))) {
            pointOfBlock.put(block.getBlockId(), block.getPointId());
        }

        // point_id -> 累计答错次数
        Map<String, Integer> score = new java.util.HashMap<>();
        for (QuestionKnowledge link : links) {
            String pointId = pointOfBlock.get(link.getBlockId());
            if (pointId == null) continue;
            score.merge(pointId, wrongByQuestion.getOrDefault(link.getQuestionId(), 1), Integer::sum);
        }

        // point_id -> 标题
        Map<String, String> titleOf = new java.util.HashMap<>();
        if (!score.isEmpty()) {
            for (KnowledgePoint p : pointMapper.selectList(
                    new QueryWrapper<KnowledgePoint>().in("point_id", score.keySet()))) {
                titleOf.put(p.getPointId(), p.getTitle());
            }
        }

        Set<String> out = new LinkedHashSet<>();
        score.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_WEAK_POINTS)
                .forEach(e -> out.add(
                        titleOf.getOrDefault(e.getKey(), e.getKey())
                                + "（" + e.getKey() + "，答错 " + e.getValue() + " 次）"));
        return new ArrayList<>(out);
    }

    /** 最近答错的题，带上题号便于用户回看。 */
    private List<String> recentWrong(Long userId) {
        List<QuestionAttempt> attempts = attemptMapper.selectList(
                new QueryWrapper<QuestionAttempt>()
                        .eq("user_id", userId)
                        .eq("result", 0)
                        .orderByDesc("answered_at")
                        .last("LIMIT " + MAX_WRONG));
        if (attempts.isEmpty()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();
        for (QuestionAttempt a : attempts) {
            Question q = questionMapper.selectById(a.getQuestionId());
            if (q == null) continue;
            String stem = q.getStem() == null ? "" : q.getStem().replaceAll("\\s+", " ");
            if (stem.length() > 40) {
                stem = stem.substring(0, 40) + "…";
            }
            out.add(q.getExamRef() + "：" + stem);
        }
        return out;
    }

    private static String subjectName(String key) {
        return switch (key) {
            case "ds" -> "数据结构";
            case "os" -> "操作系统";
            case "co" -> "计算机组成原理";
            case "cn" -> "计算机网络";
            default -> key;
        };
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String pct(Object o) {
        double v = o instanceof Number n ? n.doubleValue() : 0;
        return Math.round(v * 100) + "%";
    }
}