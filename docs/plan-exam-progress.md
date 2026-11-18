# 真题卷进度：练习 + 模考双口径（方案 1，待实现）

## 现状
- `ExamsView` 卡片已显示**练习**进度（数据源 `api.practiceQueue({ year })` → `question_attempt`）。
- **模拟考试**结果存在独立的 session 表，未汇总到卡片，用户会误以为「模考没被记录」。

## 目标
每张卷子卡显示两行，口径分开：

```
2009                              47 题
 ├ 练习  已做 31/47 · 正确率 74% · 进行中
 └ 模考  最好 118/150 · 最近 11-16 · 用时 176 分钟
```

## 待做
1. 后端 `GET /api/v1/exams/progress`：一次返回 18 卷
   - practice: done / total / correct（`question_attempt` join `question`，按 `exam_ref` 的年份聚合）
   - mock: bestScore / lastScore / lastAt / durationMinutes（模拟考试 session 表按年份聚合）
2. `ExamsView` 改为一次请求（替换现在 18 次 `practiceQueue` 并发）并渲染两行
3. 学习看板「真题卷进度」区块：已完成卷数（练习）+ 模考次数 / 最好分

## 口径约定
- 练习与模考**不合并计算**：模考是限时整卷（含综合题自评），混进日常练习会让正确率与掌握度失真。
- 「已完成」只判练习口径（47/47）；模考单独显示分数，不参与完成度。