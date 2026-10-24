package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.BlockProgress;
import com.kaoyan408.entity.DailyStat;
import com.kaoyan408.entity.KnowledgeBlock;
import com.kaoyan408.entity.KnowledgePoint;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionAttempt;
import com.kaoyan408.entity.QuestionKnowledge;
import com.kaoyan408.mapper.BlockProgressMapper;
import com.kaoyan408.mapper.DailyStatMapper;
import com.kaoyan408.mapper.KnowledgeBlockMapper;
import com.kaoyan408.mapper.KnowledgePointMapper;
import com.kaoyan408.mapper.QuestionAttemptMapper;
import com.kaoyan408.mapper.QuestionKnowledgeMapper;
import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 做题主流程：判分 → 落作答记录 → 刷新知识块掌握度与当日统计。
 */
@Service
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final QuestionAttemptMapper attemptMapper;
    private final BlockProgressMapper blockProgressMapper;
    private final DailyStatMapper dailyStatMapper;
    private final StatsService statsService;
    private final KnowledgeBlockMapper blockMapper;
    private final KnowledgePointMapper pointMapper;

    public QuestionService(QuestionMapper questionMapper, QuestionKnowledgeMapper questionKnowledgeMapper,
                           QuestionAttemptMapper attemptMapper, BlockProgressMapper blockProgressMapper,
                           DailyStatMapper dailyStatMapper, StatsService statsService,
                           KnowledgeBlockMapper blockMapper, KnowledgePointMapper pointMapper) {
        this.questionMapper = questionMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.attemptMapper = attemptMapper;
        this.blockProgressMapper = blockProgressMapper;
        this.dailyStatMapper = dailyStatMapper;
        this.statsService = statsService;
        this.blockMapper = blockMapper;
        this.pointMapper = pointMapper;
    }

    public Question requireByRef(String examRef) {
        Question question = questionMapper.selectOne(new QueryWrapper<Question>()
                .eq("exam_ref", examRef).last("LIMIT 1"));
        if (question == null) {
            throw new BusinessException(404, "未找到该题目：" + examRef);
        }
        return question;
    }

    public QuestionAttempt attemptOf(Long userId, Long questionId) {
        if (userId == null) {
            return null;
        }
        return attemptMapper.selectOne(new QueryWrapper<QuestionAttempt>()
                .eq("user_id", userId).eq("question_id", questionId).last("LIMIT 1"));
    }

    public List<String> blockIdsOf(Long questionId) {
        return questionKnowledgeMapper.selectList(new QueryWrapper<QuestionKnowledge>()
                        .eq("question_id", questionId)).stream()
                .map(QuestionKnowledge::getBlockId)
                .toList();
    }

    /**
     * 题目的关联知识点，带可读名称。
     *
     * 对外只暴露 blockId（如 kb-ds-2-2-5）对用户没有意义，界面需要的是
     * 「顺序表 · 顺序表的插入与删除」这类名称。这里在服务端一次查完，
     * 避免前端为每个 blockId 各发一次请求（一道题最多挂十几个块）。
     *
     * 返回项：{ blockId, pointId, pointTitle, subpointTitle, label }
     */
    public List<Map<String, Object>> blocksOf(Long questionId) {
        List<String> blockIds = blockIdsOf(questionId);
        if (blockIds.isEmpty()) {
            return List.of();
        }
        List<KnowledgeBlock> blocks = blockMapper.selectList(
                new QueryWrapper<KnowledgeBlock>().in("block_id", blockIds));
        // 保持与 blockIds 相同顺序，便于前端稳定展示
        Map<String, KnowledgeBlock> byId = new LinkedHashMap<>();
        for (KnowledgeBlock block : blocks) {
            byId.put(block.getBlockId(), block);
        }
        // 知识点标题单独批量查，避免 N+1
        List<String> pointIds = blocks.stream()
                .map(KnowledgeBlock::getPointId).filter(Objects::nonNull).distinct().toList();
        Map<String, String> pointTitles = new LinkedHashMap<>();
        if (!pointIds.isEmpty()) {
            for (KnowledgePoint point : pointMapper.selectList(
                    new QueryWrapper<KnowledgePoint>().in("point_id", pointIds))) {
                pointTitles.put(point.getPointId(), point.getTitle());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String blockId : blockIds) {
            KnowledgeBlock block = byId.get(blockId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("blockId", blockId);
            if (block == null) {
                item.put("pointId", null);
                item.put("pointTitle", null);
                item.put("subpointTitle", null);
                item.put("label", blockId);
                result.add(item);
                continue;
            }
            String pointTitle = pointTitles.get(block.getPointId());
            String subTitle = block.getSubpointTitle();
            item.put("pointId", block.getPointId());
            item.put("pointTitle", pointTitle);
            item.put("subpointTitle", subTitle);
            // 展示用标签：优先「知识点 · 子知识点」，退化到子知识点或知识点
            String label;
            if (pointTitle != null && subTitle != null && !subTitle.equals(pointTitle)) {
                label = pointTitle + " · " + subTitle;
            } else if (pointTitle != null) {
                label = pointTitle;
            } else if (subTitle != null) {
                label = subTitle;
            } else {
                label = blockId;
            }
            item.put("label", label);
            result.add(item);
        }
        return result;
    }

    public List<Long> answeredQuestionIds(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return attemptMapper.selectList(new QueryWrapper<QuestionAttempt>().eq("user_id", userId)).stream()
                .map(QuestionAttempt::getQuestionId)
                .toList();
    }

    /**
     * 提交答案。选择题按标准答案比对；综合题先记为完成，评分在后续里程碑实现。
     */
    @Transactional
    public Map<String, Object> submit(Long userId, Question question, String answer, Integer durationMs) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再作答。");
        }
        boolean isChoice = "choice".equals(question.getQuestionType());
        String standard = question.getAnswer() == null ? null : question.getAnswer().trim();
        boolean correct = !isChoice || (answer != null && standard != null
                && standard.equalsIgnoreCase(answer.trim()));

        QuestionAttempt attempt = attemptOf(userId, question.getId());
        boolean firstAttempt = attempt == null;
        // 本次作答前是否已经答错过（用于识别「订正」）
        boolean wasWrongBefore = !firstAttempt && attempt.getWrongCount() != null
                && attempt.getWrongCount() > 0;

        if (attempt == null) {
            attempt = new QuestionAttempt();
            attempt.setUserId(userId);
            attempt.setQuestionId(question.getId());
            attempt.setHintsUsed(0);
            attempt.setWrongCount(0);
            attempt.setRetryCorrectCount(0);
        }
        attempt.setResult(correct ? 1 : 0);
        attempt.setAnswer(answer);
        attempt.setDurationMs(durationMs);

        // 错题重做闭环的状态维护：
        //   答错 → wrongCount 累加、记录首次答错时间、清空已掌握标记（重新回到错题本）
        //   答对且此前错过 → retryCorrectCount 累加、标记已掌握（移出错题本）
        //   答对且从未错过 → 不改动上述字段
        if (!correct) {
            attempt.setWrongCount((attempt.getWrongCount() == null ? 0 : attempt.getWrongCount()) + 1);
            if (attempt.getFirstWrongAt() == null) {
                attempt.setFirstWrongAt(LocalDateTime.now());
            }
            // 再次答错说明并未真正掌握，撤销已掌握标记
            attempt.setMasteredAt(null);
        } else if (wasWrongBefore) {
            attempt.setRetryCorrectCount(
                    (attempt.getRetryCorrectCount() == null ? 0 : attempt.getRetryCorrectCount()) + 1);
            if (attempt.getMasteredAt() == null) {
                attempt.setMasteredAt(LocalDateTime.now());
            }
        }

        if (firstAttempt) {
            attemptMapper.insert(attempt);
        } else {
            // mastered_at 可能从有值改为 null，updateById 默认忽略 null 字段，
            // 因此这里用 UpdateWrapper 显式写入，避免「撤销掌握」失效。
            attemptMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<QuestionAttempt>()
                    .eq("id", attempt.getId())
                    .set("result", attempt.getResult())
                    .set("answer", attempt.getAnswer())
                    .set("duration_ms", attempt.getDurationMs())
                    .set("wrong_count", attempt.getWrongCount())
                    .set("retry_correct_count", attempt.getRetryCorrectCount())
                    .set("mastered_at", attempt.getMasteredAt())
                    .set("first_wrong_at", attempt.getFirstWrongAt()));
        }

        List<String> blockIds = blockIdsOf(question.getId());
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String blockId : blockIds) {
            blocks.add(refreshBlockProgress(userId, blockId));
        }
        // 重做也算练习量：只统计首次会让「错题重做」不计入打卡与热力图
        refreshDailyStat(userId, correct);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", question.getExamRef());
        payload.put("correct", correct);
        payload.put("standardAnswer", standard);
        payload.put("explanation", question.getExplanation());
        payload.put("firstAttempt", firstAttempt);
        payload.put("blocks", blocks);
        payload.put("stats", statsService.dashboard(userId));
        return payload;
    }

    /** 重算并返回某知识块的掌握度。 */
    public Map<String, Object> refreshBlockProgress(Long userId, String blockId) {
        int total = countBlockQuestions(blockId);
        int done = 0;
        int correct = 0;
        for (Map<String, Object> stat : questionMapper.blockProgressOfUser(userId)) {
            if (blockId.equals(String.valueOf(stat.get("blockId")))) {
                done = toInt(stat.get("done"));
                correct = toInt(stat.get("correct"));
                break;
            }
        }
        BlockProgress existing = blockProgressMapper.selectOne(new QueryWrapper<BlockProgress>()
                .eq("user_id", userId).eq("block_id", blockId).last("LIMIT 1"));
        BlockProgress progress = existing == null ? new BlockProgress() : existing;
        progress.setUserId(userId);
        progress.setBlockId(blockId);
        progress.setQuestionsTotal(total);
        progress.setQuestionsDone(done);
        progress.setQuestionsCorrect(correct);
        if (existing == null) {
            blockProgressMapper.insert(progress);
        } else {
            blockProgressMapper.update(progress, new QueryWrapper<BlockProgress>()
                    .eq("user_id", userId).eq("block_id", blockId));
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("blockId", blockId);
        view.put("total", total);
        view.put("done", done);
        view.put("correct", correct);
        return view;
    }

    private int countBlockQuestions(String blockId) {
        Long count = questionKnowledgeMapper.selectCount(
                new QueryWrapper<QuestionKnowledge>().eq("block_id", blockId));
        return count == null ? 0 : count.intValue();
    }

    private void refreshDailyStat(Long userId, boolean correct) {
        LocalDate today = LocalDate.now();
        DailyStat stat = dailyStatMapper.selectOne(new QueryWrapper<DailyStat>()
                .eq("user_id", userId).eq("stat_date", today).last("LIMIT 1"));
        if (stat == null) {
            stat = new DailyStat();
            stat.setUserId(userId);
            stat.setStatDate(today);
            stat.setQuestions(1);
            stat.setCorrect(correct ? 1 : 0);
            stat.setMinutes(0);
            dailyStatMapper.insert(stat);
        } else {
            stat.setQuestions(stat.getQuestions() + 1);
            stat.setCorrect(stat.getCorrect() + (correct ? 1 : 0));
            // daily_stat 是复合主键(user_id, stat_date)，没有 @TableId，
            // 因此不能用 updateById，必须用 QueryWrapper 指定条件。
            dailyStatMapper.update(stat, new QueryWrapper<DailyStat>()
                    .eq("user_id", userId).eq("stat_date", today));
        }
    }

    private int toInt(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}