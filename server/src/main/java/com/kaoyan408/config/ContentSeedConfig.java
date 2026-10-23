package com.kaoyan408.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 内容种子加载。
 *
 * <p>内容种子（846 题 / 知识树 / 知识块）由 {@code scripts/import-content.mjs} 生成，
 * 约 3MB，属于**数据**而非表结构，因此刻意不放进 Flyway 版本链：
 * 内容会随校准、改名、加卷而频繁重生成，若作为迁移会导致 checksum 漂移而使服务无法启动。
 * Flyway 只负责 {@code V1__init.sql} 的表结构（永不变）。
 *
 * <p>种子脚本本身幂等（先 DELETE 再 INSERT），故此处只在
 * 「题库为空」时执行，避免每次启动都重写 3MB 数据。
 * 需要强制重灌时设 {@code kaoyan.seed.force=true}。
 */
@Slf4j
@Configuration
public class ContentSeedConfig {

    @Bean
    public ApplicationRunner contentSeedRunner(DataSource dataSource,
                                               SeedProperties properties) {
        return args -> {
            if (!properties.isEnabled()) {
                log.info("内容种子加载已禁用（kaoyan.seed.enabled=false）");
                return;
            }

            boolean empty;
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM question")) {
                resultSet.next();
                empty = resultSet.getLong(1) == 0;
            } catch (Exception ex) {
                log.warn("无法检查题库状态，跳过内容种子加载：{}", ex.getMessage());
                return;
            }

            if (!empty && !properties.isForce()) {
                log.info("题库已有数据，跳过内容种子加载（需要重灌请设 kaoyan.seed.force=true）");
                return;
            }

            ClassPathResource seed = new ClassPathResource("seed/content.sql");
            if (!seed.exists()) {
                log.warn("未找到内容种子 seed/content.sql，跳过。请先执行 npm run import:content");
                return;
            }

            log.info("开始加载内容种子（约 3MB，首次启动需要数秒）…");
            long started = System.currentTimeMillis();
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(seed);
            populator.setSqlScriptEncoding("UTF-8");
            populator.setContinueOnError(false);
            try (var connection = dataSource.getConnection()) {
                populator.populate(connection);
            }
            log.info("内容种子加载完成，耗时 {} ms", System.currentTimeMillis() - started);
        };
    }
}