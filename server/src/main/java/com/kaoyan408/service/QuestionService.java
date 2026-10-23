package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.core.BusinessException;
import com.kaoyan408.entity.BlockProgress;
import com.kaoyan408.entity.DailyStat;
import com.kaoyan408.entity.Question;
import com.kaoyan408.entity.QuestionAttempt;
import com.kaoyan408.entity.QuestionKnowledge;
import com.kaoyan408.mapper.BlockProgressMapper;
import com.kaoyan408.mapper.DailyStatMapper;
import com.kaoyan408.mapper.QuestionAttemptMapper;
import com.kaoyan408.mapper.QuestionKnowledgeMapper;
import com.kaoyan408.mapper.QuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public QuestionService(QuestionMapper questionMapper, QuestionKnowledgeMapper questionKnowledgeMapper,
                           QuestionAttemptMapper attemptMapper, BlockProgressMapper blockProgressMapper,
                           DailyStatMapper dailyStatMapper, StatsService statsService) {
        this.questionMapper = questionMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
        this.attemptMapper = attemptMapper;
        this.blockProgressMapper = blockProgressMapper;
        this.dailyStatMapper = dailyStatMapper;
        this.statsService = statsService;
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
        if (attempt == null) {
            attempt = new QuestionAttempt();
            attempt.setUserId(userId);
            attempt.setQuestionId(question.getId());
            attempt.setHintsUsed(0);
        }
        attempt.setResult(correct ? 1 : 0);
        attempt.setAnswer(answer);
        attempt.setDurationMs(durationMs);
        if (firstAttempt) {
            attemptMapper.insert(attempt);
        } else {
            attemptMapper.updateById(attempt);
        }

        List<String> blockIds = blockIdsOf(question.getId());
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String blockId : blockIds) {
            blocks.add(refreshBlockProgress(userId, blockId));
        }
        if (firstAttempt) {
            refreshDailyStat(userId, correct);
        }

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