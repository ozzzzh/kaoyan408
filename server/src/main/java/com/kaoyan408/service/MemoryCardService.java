package com.kaoyan408.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.MemoryCard;
import com.kaoyan408.mapper.MemoryCardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 背诵卡片与间隔重复调度。
 *
 * <p>调度用 Leitner 记忆盒：盒号 0–7，答对进一盒、答错退回第 0 盒，
 * 盒号决定下次复习间隔（天数）。
 * <p>选 Leitner 而非 SM-2 是因为自评只有「记住了 / 没记住」两档，
 * 没有 SM-2 需要的 0–5 难度评分，硬套会让参数失真。
 */
@Service
public class MemoryCardService {

    /** 各记忆盒对应的复习间隔（天）。索引即盒号。 */
    private static final int[] BOX_INTERVALS = {0, 1, 2, 4, 7, 15, 30, 60};

    /** 单次复习最多取多少张，避免一次拉爆。 */
    private static final int DUE_LIMIT = 50;

    private static final String[] SUBJECTS = {"ds", "os", "co", "cn"};
    private static final Map<String, String> SUBJECT_NAMES = Map.of(
            "ds", "数据结构",
            "os", "操作系统",
            "co", "计算机组成原理",
            "cn", "计算机网络");

    private final MemoryCardMapper cardMapper;

    public MemoryCardService(MemoryCardMapper cardMapper) {
        this.cardMapper = cardMapper;
    }

    /** 到期复习队列（含从未复习的新卡）。 */
    public Map<String, Object> due(Long userId, String subject) {
        List<Map<String, Object>> cards = dueCards(userId, subject, DUE_LIMIT);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : cards) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cardId", row.get("cardId"));
            item.put("blockId", row.get("blockId"));
            item.put("type", row.get("type"));
            item.put("content", row.get("content"));
            item.put("subpointTitle", row.get("subpointTitle"));
            item.put("pointId", row.get("pointId"));
            item.put("pointTitle", row.get("pointTitle"));
            item.put("subject", row.get("subject"));
            item.put("box", toLong(row.get("box")));
            item.put("reviewCount", toLong(row.get("reviewCount")));
            item.put("correctCount", toLong(row.get("correctCount")));
            items.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", items.size());
        payload.put("items", items);
        return payload;
    }

    /** 到期卡片（内部复用，按科目可过滤）。 */
    private List<Map<String, Object>> dueCards(Long userId, String subject, int limit) {
        List<Map<String, Object>> rows = cardMapper.dueCards(userId, limit);
        if (subject == null || subject.isBlank()) {
            return rows;
        }
        return rows.stream()
                .filter(r -> subject.equals(String.valueOf(r.get("subject"))))
                .toList();
    }

    /**
     * 复习一张卡片。
     * remembered=true：进一盒（最多 7），下次间隔拉长；
     * remembered=false：退回第 0 盒，明天再复习。
     */
    @Transactional
    public Map<String, Object> review(Long userId, Long cardId, boolean remembered) {
        MemoryCard card = cardMapper.selectOne(new QueryWrapper<MemoryCard>()
                .eq("id", cardId).eq("user_id", userId).last("LIMIT 1"));
        if (card == null) {
            throw new com.kaoyan408.core.BusinessException(404, "未找到该背诵卡片。");
        }

        int box = card.getBox() == null ? 0 : card.getBox();
        if (remembered) {
            box = Math.min(box + 1, BOX_INTERVALS.length - 1);
        } else {
            box = 0;
        }

        LocalDate next = LocalDate.now().plusDays(BOX_INTERVALS[box]);
        card.setBox(box);
        card.setNextReviewDate(next);
        card.setLastReviewedAt(LocalDateTime.now());
        card.setReviewCount((card.getReviewCount() == null ? 0 : card.getReviewCount()) + 1);
        if (remembered) {
            card.setCorrectCount((card.getCorrectCount() == null ? 0 : card.getCorrectCount()) + 1);
        }
        cardMapper.updateById(card);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cardId", cardId);
        payload.put("box", box);
        payload.put("nextReviewDate", next);
        payload.put("intervalDays", BOX_INTERVALS[box]);
        return payload;
    }

    /** 背诵概览：总数、到期数、牢固数、分科目分布。 */
    public Map<String, Object> summary(Long userId) {
        Map<String, Object> row = userId == null ? null : cardMapper.cardSummary(userId);
        long total = row == null ? 0 : toLong(row.get("total"));
        long solid = row == null ? 0 : toLong(row.get("solid"));
        long due = row == null ? 0 : toLong(row.get("due"));
        long reviews = row == null ? 0 : toLong(row.get("reviews"));
        long corrects = row == null ? 0 : toLong(row.get("corrects"));

        Map<String, Long> bySubject = new LinkedHashMap<>();
        if (userId != null) {
            for (Map<String, Object> r : cardMapper.cardsBySubject(userId)) {
                bySubject.put(String.valueOf(r.get("subject")), toLong(r.get("total")));
            }
        }

        List<Map<String, Object>> subjects = new ArrayList<>();
        for (String subject : SUBJECTS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", subject);
            item.put("name", SUBJECT_NAMES.getOrDefault(subject, subject));
            item.put("total", bySubject.getOrDefault(subject, 0L));
            subjects.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("due", due);
        payload.put("solid", solid);
        payload.put("reviews", reviews);
        payload.put("corrects", corrects);
        payload.put("accuracy", reviews == 0 ? 0.0 : Math.round(corrects * 1000.0 / reviews) / 10.0);
        payload.put("subjects", subjects);
        return payload;
    }

    /**
     * 把适合背诵的知识块加入卡片（callout 要点 / formula 公式）。
     * 已存在的会被跳过（表上有 (user_id, block_id) 唯一键兜底）。
     */
    @Transactional
    public Map<String, Object> add(Long userId, String pointId, String subject, Integer limit) {
        int take = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> blocks = cardMapper.addableBlocks(
                userId, pointId, subject, take);

        int added = 0;
        for (Map<String, Object> row : blocks) {
            MemoryCard card = new MemoryCard();
            card.setUserId(userId);
            card.setBlockId(String.valueOf(row.get("blockId")));
            card.setBox(0);
            card.setReviewCount(0);
            card.setCorrectCount(0);
            card.setDifficulty(0);
            // 新卡立即到期，进队列即可复习
            card.setNextReviewDate(LocalDate.now());
            card.setCreatedAt(LocalDateTime.now());
            try {
                cardMapper.insert(card);
                added++;
            } catch (Exception ignored) {
                // 并发或重复添加时唯一键会报错，跳过即可
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("added", added);
        payload.put("candidates", blocks.size());
        return payload;
    }

    /** 移除一张卡片。 */
    @Transactional
    public Map<String, Object> remove(Long userId, Long cardId) {
        int deleted = cardMapper.delete(new QueryWrapper<MemoryCard>()
                .eq("id", cardId).eq("user_id", userId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deleted", deleted);
        return payload;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}