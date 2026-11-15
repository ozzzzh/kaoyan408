package com.kaoyan408.service;

import com.kaoyan408.entity.MockAnswer;
import com.kaoyan408.entity.MockSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游客的模拟考试会话与作答（只存内存，不落库）。
 *
 * <p>为什么单独放一层而不是把 userId 为 null 的情况散落在 MockExamService 各处：
 * 考试流程要读写 6 个方法，逐个插 null 判断会让主流程难读且容易漏。
 * 这里用内存表替代数据库，Service 内部对两者的调用形态保持一致。
 *
 * <p>代价：服务重启后游客的考试消失。这是可接受的——游客没有账号，
 * 交卷结果只需当场展示一次，也不进入历史成绩。
 */
@Service
public class GuestMockStore {

    /** 会话上限，防止反复开考把内存撑爆。 */
    private static final int MAX_SESSIONS = 200;

    /** 会话有效期，超过即回收（默认限时 180 分钟，留足余量）。 */
    private static final long TTL_MILLIS = 4 * 60 * 60 * 1000L;

    private final Map<Long, MockSession> sessions = new ConcurrentHashMap<>();
    /** sessionId -> (questionId -> 作答) */
    private final Map<Long, Map<Long, MockAnswer>> answers = new ConcurrentHashMap<>();
    private final Map<Long, Long> createdAt = new ConcurrentHashMap<>();

    /** 会话 id 从负数开始，与数据库自增 id（正数）天然区分。 */
    private long nextId = -1;

    public synchronized MockSession create(int examYear, int durationMinutes) {
        evictExpired();
        if (sessions.size() >= MAX_SESSIONS) {
            evictOldest();
        }
        long id = nextId--;
        MockSession session = new MockSession();
        session.setId(id);
        session.setUserId(null);
        session.setExamYear(examYear);
        session.setStartedAt(LocalDateTime.now());
        session.setDurationMinutes(durationMinutes);
        session.setStatus("ongoing");
        session.setAutoScore(0);
        session.setSelfScore(0);
        session.setTimedOut(0);
        sessions.put(id, session);
        answers.put(id, new LinkedHashMap<>());
        createdAt.put(id, System.currentTimeMillis());
        return session;
    }

    public MockSession get(Long id) {
        if (id == null || id >= 0) {
            return null;
        }
        MockSession session = sessions.get(id);
        if (session != null) {
            Long born = createdAt.get(id);
            if (born != null && System.currentTimeMillis() - born > TTL_MILLIS) {
                remove(id);
                return null;
            }
        }
        return session;
    }

    /** 会话对象本身在内存里是引用的，改完无需回写；此方法仅为语义清晰保留。 */
    public void put(MockSession session) {
        if (session != null && session.getId() != null && session.getId() < 0) {
            sessions.put(session.getId(), session);
        }
    }

    public List<MockAnswer> answersOf(Long sessionId) {
        Map<Long, MockAnswer> map = answers.get(sessionId);
        return map == null ? new ArrayList<>() : new ArrayList<>(map.values());
    }

    public MockAnswer answerOf(Long sessionId, Long questionId) {
        Map<Long, MockAnswer> map = answers.get(sessionId);
        return map == null ? null : map.get(questionId);
    }

    public void saveAnswer(Long sessionId, MockAnswer answer) {
        answers.computeIfAbsent(sessionId, k -> new LinkedHashMap<>())
                .put(answer.getQuestionId(), answer);
    }

    public void remove(Long id) {
        if (id != null) {
            sessions.remove(id);
            answers.remove(id);
            createdAt.remove(id);
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        createdAt.forEach((id, born) -> {
            if (now - born > TTL_MILLIS) {
                remove(id);
            }
        });
    }

    private void evictOldest() {
        createdAt.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .ifPresent(this::remove);
    }
}