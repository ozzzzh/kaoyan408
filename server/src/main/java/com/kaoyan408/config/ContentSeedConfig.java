package com.kaoyan408.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

/**
 * 内容种子加载。
 *
 * <p>内容种子（846 题 / 知识树 / 知识块）由 {@code scripts/import-content.mjs} 生成，
 * 约 3MB，属于**数据**而非表结构，因此刻意不放进 Flyway 版本链：
 * 内容会随校准、改名、加卷而频繁重生成，若作为迁移会导致 checksum 漂移而使服务无法启动。
 * Flyway 只负责 {@code V1__init.sql} 的表结构（永不变）。
 *
 * <p>这里补了四件原来没有的保护，都是「一旦触发就不可逆」的场景：
 * <ol>
 *   <li><b>事务</b>：整份种子在一个事务里灌完。脚本有 1600+ 条 INSERT，中途失败原来会留下
 *       半套题库，而下次启动因为 count&gt;0 直接跳过，数据库就永久停在半成品状态。</li>
 *   <li><b>互斥锁</b>：用 MySQL 的 {@code GET_LOCK} 串行化。多实例同时启动时原来会各自
 *       判定「题库为空」然后同时 DELETE+INSERT，结果互相踩。</li>
 *   <li><b>版本指纹</b>：把种子文件的 SHA-256 记进 {@code app_meta}。内容换了（指纹变化）
 *       会自动重灌，不用记着加 force；内容没换则跳过，省掉每次启动的 3MB 写入。</li>
 *   <li><b>用户数据守卫</b>：{@code force=true} 会 DELETE 题库，而 question.id 是自增的，
 *       重灌后 id 全变，{@code question_attempt} / {@code mock_answer} 会变成孤儿记录，
 *       错题本与掌握度静默错乱。所以一旦检测到已有作答数据，除非显式确认，
 *       否则拒绝重灌并给出明确提示。</li>
 * </ol>
 */
@Slf4j
@Configuration
public class ContentSeedConfig {

    private static final String SEED_LOCK_NAME = "kaoyan408_seed";
    private static final int LOCK_TIMEOUT_SECONDS = 120;
    private static final long LOCK_WAIT_MILLIS = 180_000L;
    private static final String CHECKSUM_KEY = "seed.content.sha256";

    @Bean
    public ApplicationRunner contentSeedRunner(DataSource dataSource, SeedProperties properties) {
        return args -> {
            if (!properties.isEnabled()) {
                log.info("内容种子加载已禁用（kaoyan.seed.enabled=false）");
                return;
            }

            ClassPathResource seed = new ClassPathResource("seed/content.sql");
            if (!seed.exists()) {
                log.warn("未找到内容种子 seed/content.sql，跳过。请先执行 npm run import:content");
                return;
            }

            String checksum = checksumOf(seed);
            try (Connection connection = dataSource.getConnection()) {
                // 1) 互斥：多实例同时启动时只有一个能灌，其余等它灌完再看指纹决定是否跳过。
                if (!tryLock(connection)) {
                    log.warn("未能在 {}s 内拿到内容种子锁，跳过本次加载（另一个实例可能正在灌）",
                            LOCK_TIMEOUT_SECONDS);
                    return;
                }
                try {
                    seedWithinLock(connection, seed, checksum, properties);
                } finally {
                    releaseLock(connection);
                }
            } catch (Exception ex) {
                // 连不上库等情况：保持原有「不让服务起不来」的行为，但要留下明确日志
                log.warn("内容种子加载失败，服务继续启动：{}", ex.getMessage());
            }
        };
    }

    private void seedWithinLock(Connection connection, ClassPathResource seed,
                                String checksum, SeedProperties properties) throws Exception {
        long questions = count(connection, "SELECT COUNT(*) FROM question");
        boolean empty = questions == 0;
        String applied = metaGet(connection, CHECKSUM_KEY);
        boolean userData = hasUserData(connection);

        if (!empty) {
            if (properties.isForce()) {
                // 显式要求重灌：仍然要过用户数据守卫
                if (userData && !properties.isForceWithUserData()) {
                    log.error("检测到已有作答数据，kaoyan.seed.force=true 会重灌题库并让这些记录指向不存在的题目。"
                            + "已跳过。确认要丢弃历史，请显式设置 kaoyan.seed.force-with-user-data=true。");
                    return;
                }
            } else if (applied == null) {
                // 升级到「指纹」机制后的第一次启动：库里已有内容，先把当前指纹记下来，
                // 不重灌。否则这次升级会白白让 846 题换一遍 id，把作答记录变成孤儿。
                // 这里不需要显式 commit：刚建的表还没进过事务，连接仍是 autoCommit=true，
                // 语句本身已经落库。之前在这里调 commit() 会抛「Can't call commit when
                // autocommit=true」，被外层 catch 吞成一条 WARN —— 功能没坏，但日志会误导人。
                metaPut(connection, CHECKSUM_KEY, checksum);
                log.info("首次记录内容种子指纹（{}…），题库已有 {} 题，跳过重灌", shortOf(checksum), questions);
                return;
            } else if (applied.equals(checksum)) {
                log.info("内容种子已是最新（指纹 {}…，{} 题），跳过", shortOf(checksum), questions);
                return;
            } else {
                // 指纹变了：说明内容确实更新过
                if (userData && !properties.isForceWithUserData()) {
                    log.error("内容种子指纹已变化（库内 {}… / 文件 {}…），但检测到作答数据；"
                            + "重灌会让历史记录指向不存在的题目，已跳过。"
                            + "请先导出个人数据，或显式设置 kaoyan.seed.force-with-user-data=true。",
                            shortOf(applied), shortOf(checksum));
                    return;
                }
                log.info("内容种子指纹变化（库内 {}… / 文件 {}…），重新灌入",
                        shortOf(applied), shortOf(checksum));
            }
        }

        long started = System.currentTimeMillis();
        log.info("开始加载内容种子（约 3MB，事务内执行，中途失败会整体回滚）…");
        connection.setAutoCommit(false);
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(seed);
            populator.setSqlScriptEncoding("UTF-8");
            populator.setContinueOnError(false);
            populator.populate(connection);
            metaPut(connection, CHECKSUM_KEY, checksum);
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            log.error("内容种子加载失败，已回滚，题库保持原状：{}", ex.getMessage());
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
        log.info("内容种子加载完成，耗时 {} ms", System.currentTimeMillis() - started);
    }

    /** 是否已有用户作答数据（有则 force 重灌会破坏历史）。 */
    private boolean hasUserData(Connection connection) {
        try {
            return count(connection, "SELECT COUNT(*) FROM question_attempt") > 0
                    || count(connection, "SELECT COUNT(*) FROM mock_answer") > 0
                    || count(connection, "SELECT COUNT(*) FROM block_progress") > 0;
        } catch (Exception ex) {
            // 表还不在（首次部署）：当作没有用户数据
            return false;
        }
    }

    private boolean tryLock(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, SEED_LOCK_NAME);
            ps.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (Exception ex) {
            // 非 MySQL 或权限不足时不阻塞启动，降级为不加锁
            log.debug("获取内容种子锁失败（按无锁继续）：{}", ex.getMessage());
            return true;
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, SEED_LOCK_NAME);
            ps.executeQuery();
        } catch (Exception ignored) {
            // 连接关闭时 MySQL 会自动释放会话级锁
        }
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String metaGet(Connection connection, String key) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM app_meta WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception ex) {
            // app_meta 由 Flyway 迁移创建；异常时按「没有记录」处理
            return null;
        }
    }

    private void metaPut(Connection connection, String key, String value) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO app_meta (k, value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /** 种子文件指纹：内容变了才重灌，避免每次启动都写 3MB。 */
    private String checksumOf(ClassPathResource seed) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = seed.getInputStream();
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[64 * 1024];
            while (digestIn.read(buffer) >= 0) {
                // 只为推进摘要
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String shortOf(String value) {
        return value == null ? "(无)" : value.substring(0, Math.min(12, value.length()));
    }
}
