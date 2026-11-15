package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.BlockProgress;
import com.kaoyan408.entity.DailyStat;
import com.kaoyan408.entity.MemoryCard;
import com.kaoyan408.entity.QuestionAttempt;
import com.kaoyan408.mapper.BlockProgressMapper;
import com.kaoyan408.mapper.DailyStatMapper;
import com.kaoyan408.mapper.MemoryCardMapper;
import com.kaoyan408.mapper.QuestionAttemptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人数据管理：导出与清空。
 *
 * <p>这两件事都是「用户对自己数据的处置权」——导出用于备份或换设备，
 * 清空用于重新开始。因为清空不可恢复，接口按类别分开，
 * 由前端做二次确认，而不是提供一个「一键全删」。
 */
@Service
public class UserDataService {

    private final QuestionAttemptMapper attemptMapper;
    private final BlockProgressMapper blockProgressMapper;
    private final DailyStatMapper dailyStatMapper;
    private final MemoryCardMapper cardMapper;

    public UserDataService(QuestionAttemptMapper attemptMapper,
                           BlockProgressMapper blockProgressMapper,
                           DailyStatMapper dailyStatMapper,
                           MemoryCardMapper cardMapper) {
        this.attemptMapper = attemptMapper;
        this.blockProgressMapper = blockProgressMapper;
        this.dailyStatMapper = dailyStatMapper;
        this.cardMapper = cardMapper;
    }

    /** 各类数据的条数，用于在界面上显示「将删除 N 条」并在为空时禁用按钮。 */
    public Map<String, Object> counts(Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempts", attemptMapper.selectCount(
                new QueryWrapper<QuestionAttempt>().eq("user_id", userId)));
        payload.put("blockProgress", blockProgressMapper.selectCount(
                new QueryWrapper<BlockProgress>().eq("user_id", userId)));
        payload.put("dailyStats", dailyStatMapper.selectCount(
                new QueryWrapper<DailyStat>().eq("user_id", userId)));
        payload.put("memoryCards", cardMapper.selectCount(
                new QueryWrapper<MemoryCard>().eq("user_id", userId)));
        return payload;
    }

    /**
     * 导出全部个人数据。
     *
     * 返回结构化 JSON 而非 CSV：数据有嵌套（作答记录带题目引用、
     * 背诵卡片带盒号），JSON 能无损表达，用户想转 CSV 也容易。
     */
    public Map<String, Object> export(Long userId, String username) {
        List<Map<String, Object>> attempts = new ArrayList<>();
        for (QuestionAttempt attempt : attemptMapper.selectList(
                new QueryWrapper<QuestionAttempt>().eq("user_id", userId).orderByAsc("id"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", attempt.getQuestionId());
            row.put("result", attempt.getResult());
            row.put("answer", attempt.getAnswer());
            row.put("answeredAt", attempt.getAnsweredAt());
            row.put("wrongCount", attempt.getWrongCount());
            row.put("retryCorrectCount", attempt.getRetryCorrectCount());
            row.put("masteredAt", attempt.getMasteredAt());
            attempts.add(row);
        }

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (BlockProgress progress : blockProgressMapper.selectList(
                new QueryWrapper<BlockProgress>().eq("user_id", userId))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("blockId", progress.getBlockId());
            row.put("questionsTotal", progress.getQuestionsTotal());
            row.put("questionsDone", progress.getQuestionsDone());
            row.put("questionsCorrect", progress.getQuestionsCorrect());
            blocks.add(row);
        }

        List<Map<String, Object>> daily = new ArrayList<>();
        for (DailyStat stat : dailyStatMapper.selectList(
                new QueryWrapper<DailyStat>().eq("user_id", userId).orderByAsc("stat_date"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", stat.getStatDate());
            row.put("questions", stat.getQuestions());
            row.put("correct", stat.getCorrect());
            daily.add(row);
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        for (MemoryCard card : cardMapper.selectList(
                new QueryWrapper<MemoryCard>().eq("user_id", userId).orderByAsc("id"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("blockId", card.getBlockId());
            row.put("box", card.getBox());
            row.put("nextReviewDate", card.getNextReviewDate());
            row.put("reviewCount", card.getReviewCount());
            row.put("correctCount", card.getCorrectCount());
            cards.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("exportedAt", java.time.LocalDateTime.now().toString());
        payload.put("attempts", attempts);
        payload.put("blockProgress", blocks);
        payload.put("dailyStats", daily);
        payload.put("memoryCards", cards);
        return payload;
    }

    /**
     * 清空指定类别。
     *
     * @param scope attempts（作答记录+知识点掌握+每日统计）/ memory（背诵卡片）/ all
     * @return 各表实际删除条数
     */
    @Transactional
    public Map<String, Object> clear(Long userId, String scope) {
        Map<String, Object> removed = new LinkedHashMap<>();
        String target = scope == null ? "" : scope;

        // 作答记录、知识点掌握、每日统计三者互相依赖：
        // 掌握度与每日统计都是从作答记录推出来的，只删其中一张会留下不一致的派生数据。
        // 因此它们始终同进同退。
        if ("attempts".equals(target) || "all".equals(target)) {
            removed.put("attempts", attemptMapper.delete(
                    new QueryWrapper<QuestionAttempt>().eq("user_id", userId)));
            removed.put("blockProgress", blockProgressMapper.delete(
                    new QueryWrapper<BlockProgress>().eq("user_id", userId)));
            removed.put("dailyStats", dailyStatMapper.delete(
                    new QueryWrapper<DailyStat>().eq("user_id", userId)));
        }
        if ("memory".equals(target) || "all".equals(target)) {
            removed.put("memoryCards", cardMapper.delete(
                    new QueryWrapper<MemoryCard>().eq("user_id", userId)));
        }
        return removed;
    }
}