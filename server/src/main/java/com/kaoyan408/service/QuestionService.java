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

    /**
     * 整卷场景：一次性把「题目 -> 知识块展示信息」全部查出来（避免逐题 3 条批量查询）。
     *
     * @param blockIdsByQuestion 题目 id -> 该题关联的 blockId 列表（顺序保留）
     * @return 题目 id -> 与 blocksOf 相同结构的知识块视图列表
     */
    public Map<Long, List<Map<String, Object>>> blocksOfMany(Map<Long, List<String>> blockIdsByQuestion) {
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (blockIdsByQuestion == null || blockIdsByQuestion.isEmpty()) {
            return result;
        }
        java.util.Set<String> allBlockIds = new java.util.LinkedHashSet<>();
        blockIdsByQuestion.values().forEach(allBlockIds::addAll);
        if (allBlockIds.isEmpty()) {
            blockIdsByQuestion.forEach((questionId, ids) -> result.put(questionId, List.of()));
            return result;
        }

        Map<String, KnowledgeBlock> byId = new LinkedHashMap<>();
        for (KnowledgeBlock block : blockMapper.selectList(
                new QueryWrapper<KnowledgeBlock>().in("block_id", allBlockIds))) {
            byId.put(block.getBlockId(), block);
        }
        List<String> pointIds = byId.values().stream()
                .map(KnowledgeBlock::getPointId).filter(Objects::nonNull).distinct().toList();
        Map<String, String> pointTitles = new LinkedHashMap<>();
        if (!pointIds.isEmpty()) {
            for (KnowledgePoint point : pointMapper.selectList(
                    new QueryWrapper<KnowledgePoint>().in("point_id", pointIds))) {
                pointTitles.put(point.getPointId(), point.getTitle());
            }
        }

        for (Map.Entry<Long, List<String>> entry : blockIdsByQuestion.entrySet()) {
            List<Map<String, Object>> views = new ArrayList<>();
            for (String blockId : entry.getValue()) {
                views.add(blockView(blockId, byId.get(blockId), pointTitles));
            }
            result.put(entry.getKey(), views);
        }
        return result;
    }

    /** 单个知识块的展示视图；blocksOf 与 blocksOfMany 共用同一份格式。 */
    private Map<String, Object> blockView(String blockId, KnowledgeBlock block, Map<String, String> pointTitles) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("blockId", blockId);
        if (block == null) {
            item.put("pointId", null);
            item.put("pointTitle", null);
            item.put("subpointTitle", null);
            item.put("label", blockId);
            return item;
        }
        String pointTitle = pointTitles.get(block.getPointId());
        String subTitle = block.getSubpointTitle();
        item.put("pointId", block.getPointId());
        item.put("pointTitle", pointTitle);
        item.put("subpointTitle", subTitle);
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
        return item;
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
        boolean isChoice = "choice".equals(question.getQuestionType());
        String standard = question.getAnswer() == null ? null : question.getAnswer().trim();
        boolean correct = !isChoice || (answer != null && standard != null
                && standard.equalsIgnoreCase(answer.trim()));

        // 游客：只判分并返回解析，不写任何记录。
        // 之前这里直接抛 401，导致游客能打开刷题页却答不出结果，体验是断的。
        // 判分逻辑与登录用户完全一致，差别只在于「不落库」。
        if (userId == null) {
            return guestResult(question, answer, correct, standard);
        }

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
        List<Map<String, Object>> blocks = refreshBlockProgressBatch(userId, blockIds);
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

    /**
     * 游客作答的结果：字段与登录用户保持一致，便于前端复用同一套渲染逻辑。
     *
     * <p>不提供的字段：blocks（知识点掌握）、stats（未登录时的统计恒为 0，给了反而误导）。
     * 前端据此可以判断「这是游客，不显示保存相关提示」。
     */
    private Map<String, Object> guestResult(Question question, String answer,
                                            boolean correct, String standard) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", question.getExamRef());
        payload.put("correct", correct);
        payload.put("standardAnswer", standard);
        payload.put("explanation", question.getExplanation());
        // 游客没有历史记录，每次都是「首次」
        payload.put("firstAttempt", true);
        payload.put("blocks", List.of());
        // 明确告知前端：本次结果不会被保存
        payload.put("guest", true);
        payload.put("saved", false);
        return payload;
    }

    /**
     * 一次作答后批量刷新该题关联的所有知识块掌握度。
     *
     * <p>原来是「每块调一次 {@link #refreshBlockProgress}」，而那个方法每次都要跑
     * 一次全用户聚合（blockProgressOfUser）+ 一次计数 + 一次 selectOne + 一次写。
     * 一道题挂 10 个块就是 30+ 条 SQL，其中 10 条还是全表聚合——这是最热的写路径，
     * 也是单题提交体感最直接的瓶颈。
     *
     * <p>现在读取全部批量化（3 条 SQL），只有写入仍是按行 upsert（块数条）。
     */
    private List<Map<String, Object>> refreshBlockProgressBatch(Long userId, List<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return List.of();
        }

        // 1) 每题关联题量，一条 GROUP BY 拿到全部块
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map<String, Object> row : questionMapper.questionCountsOfBlocks(blockIds)) {
            totals.put(String.valueOf(row.get("blockId")), toInt(row.get("total")));
        }

        // 2) 该用户在这些块上的作答聚合，一条 SQL（原来是每块一次全用户聚合）
        Map<String, int[]> doneAndCorrect = new LinkedHashMap<>();
        for (Map<String, Object> stat : questionMapper.blockProgressOfUser(userId)) {
            String blockId = String.valueOf(stat.get("blockId"));
            if (blockIds.contains(blockId)) {
                doneAndCorrect.put(blockId, new int[]{toInt(stat.get("done")), toInt(stat.get("correct"))});
            }
        }

        // 3) 已有的 block_progress 行，一条 IN 查询
        Map<String, BlockProgress> existing = new LinkedHashMap<>();
        for (BlockProgress row : blockProgressMapper.selectList(new QueryWrapper<BlockProgress>()
                .eq("user_id", userId).in("block_id", blockIds))) {
            existing.put(row.getBlockId(), row);
        }

        List<Map<String, Object>> views = new ArrayList<>();
        for (String blockId : blockIds) {
            int total = totals.getOrDefault(blockId, 0);
            int[] stat = doneAndCorrect.getOrDefault(blockId, new int[]{0, 0});
            BlockProgress row = existing.get(blockId);
            BlockProgress progress = row == null ? new BlockProgress() : row;
            progress.setUserId(userId);
            progress.setBlockId(blockId);
            progress.setQuestionsTotal(total);
            progress.setQuestionsDone(stat[0]);
            progress.setQuestionsCorrect(stat[1]);
            if (row == null) {
                blockProgressMapper.insert(progress);
            } else {
                blockProgressMapper.update(progress, new QueryWrapper<BlockProgress>()
                        .eq("user_id", userId).eq("block_id", blockId));
            }

            Map<String, Object> view = new LinkedHashMap<>();
            view.put("blockId", blockId);
            view.put("total", total);
            view.put("done", stat[0]);
            view.put("correct", stat[1]);
            views.add(view);
        }
        return views;
    }

    /** 重算并返回某知识块的掌握度（单块入口，保留给其他调用方）。 */
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

    /**
     * 当日打卡统计 +1。
     *
     * <p>原来是「先 select 出整行、在 Java 里 +1、再写回绝对值」。两个标签页同时交卷时，
     * 后写到的那次会覆盖前一次的结果，表现为「做了两道题、打卡只 +1」——典型的丢更新，
     * 而且悄无声息。现在改成 SQL 层的原子自增：questions = questions + 1。
     */
    private void refreshDailyStat(Long userId, boolean correct) {
        LocalDate today = LocalDate.now();
        int correctInc = correct ? 1 : 0;

        int updated = dailyStatMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DailyStat>()
                .eq("user_id", userId).eq("stat_date", today)
                .setSql("questions = questions + 1, correct = correct + " + correctInc));
        if (updated > 0) {
            return;
        }

        DailyStat stat = new DailyStat();
        stat.setUserId(userId);
        stat.setStatDate(today);
        stat.setQuestions(1);
        stat.setCorrect(correctInc);
        stat.setMinutes(0);
        try {
            dailyStatMapper.insert(stat);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发下另一请求刚插入了今天的行，退化为原子自增
            dailyStatMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DailyStat>()
                    .eq("user_id", userId).eq("stat_date", today)
                    .setSql("questions = questions + 1, correct = correct + " + correctInc));
        }
    }

    private int toInt(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}