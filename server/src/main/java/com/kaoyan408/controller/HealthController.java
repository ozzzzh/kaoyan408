package com.kaoyan408.controller;

import com.kaoyan408.core.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。
 *
 * <p>原来这里只回一个写死的 {@code status: UP}，连数据库都不看：内容种子还在灌的
 * 那几十秒里它照样报 UP，数据库断了也照样报 UP。它只能证明「进程活着」，
 * 不能当部署门禁。现在补一次真实的 {@code SELECT 1}，并顺带返回题库条数——
 * 这个数字同时回答了「迁移与种子是否就位」。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Value("${spring.application.name:kaoyan408-server}")
    private String appName;

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", appName);
        data.put("version", "0.1.0");

        long started = System.currentTimeMillis();
        String status;
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("db", one != null && one == 1 ? "up" : "unknown");
            Integer questions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question", Integer.class);
            data.put("questions", questions);
            status = "UP";
        } catch (Exception e) {
            data.put("db", "down");
            data.put("error", e.getClass().getSimpleName());
            status = "DOWN";
        }
        data.put("checkMillis", System.currentTimeMillis() - started);
        data.put("status", status);
        return Result.ok(data);
    }
}
