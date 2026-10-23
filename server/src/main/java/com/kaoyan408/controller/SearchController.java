package com.kaoyan408.controller;

import com.kaoyan408.core.Result;
import com.kaoyan408.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 全文搜索。无需登录：题库与知识正文都是公开内容。
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 综合搜索：知识点 / 题目 / 正文片段三组结果。 */
    @GetMapping
    public Result<Map<String, Object>> search(@RequestParam(defaultValue = "") String q) {
        return Result.ok(searchService.search(q));
    }

    /** 输入联想（工作台搜索框下拉）。 */
    @GetMapping("/suggest")
    public Result<List<Map<String, Object>>> suggest(@RequestParam(defaultValue = "") String q) {
        return Result.ok(searchService.suggest(q));
    }
}