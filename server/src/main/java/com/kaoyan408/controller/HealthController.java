package com.kaoyan408.controller;

import com.kaoyan408.core.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Value("${spring.application.name:kaoyan408-server}")
    private String appName;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", appName);
        data.put("version", "0.1.0");
        data.put("status", "UP");
        return Result.ok(data);
    }
}