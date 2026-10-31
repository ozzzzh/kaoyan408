package com.kaoyan408.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kaoyan408.entity.KnowledgePoint;
import com.kaoyan408.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 构造发给模型的系统提示。
 *
 * <p>核心目标是让模型的回答**能跳回本站知识点**。做法是把 147 个知识点的
 * id 与标题列进提示，并要求模型在谈到某个知识点时输出
 * {@code [[kp:id|标题]]} 标记，前端再把它渲染成可点击卡片。
 *
 * <p>为什么要求「只能从清单里选」：模型不知道本站有哪些知识点，
 * 不限制的话会编出不存在的 id，点进去就是 404。清单是唯一的合法来源。
 *
 * <p>关于长度：147 个知识点全列出来约 3K token。用 id + 短标题而不是全路径，
 * 是权衡后的结果——全路径会让提示涨到 8K 以上，每次对话都多花这部分钱。
 */
@Component
public class AiPromptBuilder {

    private final KnowledgePointMapper pointMapper;

    /** 知识点清单每次请求都读库，用实例缓存避免重复查询（内容不常变）。 */
    private volatile String knowledgeCatalog;

    public AiPromptBuilder(KnowledgePointMapper pointMapper) {
        this.pointMapper = pointMapper;
    }

    /**
     * 构造系统提示。
     *
     * @param context 学习上下文摘要；未登录时传 null
     */
    public String build(String context) {
        StringBuilder sb = new StringBuilder(4096);

        sb.append("""
                你是「题知链 408」的学习助手，服务对象是准备计算机统考 408 的考生。
                408 包含四门：数据结构、计算机组成原理、操作系统、计算机网络，满分 150。

                """);

        // 当前时间。
        // 不注入的话模型不知道自己处在哪一天，被问「现在几月」「还剩几个月」时
        // 只能回答「我无法获取实时时间」——对一个备考规划助手来说这是硬伤。
        // 每次请求现算，服务长期运行也不会给出过期日期。
        LocalDate today = LocalDate.now();
        sb.append("今天是 ").append(today.getYear()).append(" 年 ")
                .append(today.getMonthValue()).append(" 月 ")
                .append(today.getDayOfMonth()).append(" 日，")
                .append(weekdayOf(today)).append("。\n")
                .append("408 初试通常安排在 12 月的第三个或第四个周末，具体以教育部当年公告为准；")
                .append("推算复习周期时以这个时间为基准，并说明是推算值。\n");

        sb.append("""

                ## 回答要求
                1. 用中文回答，语气像一位耐心的助教，不要客套开场白。
                2. 讲清原理，必要时给例子、对比、复杂度；涉及公式用 $...$ 或 $$...$$ 包起来。
                3. 回答长度看问题复杂度，简单问题三五句话即可，不要为凑字数展开。
                4. 不要编造 408 考试大纲之外的内容；不确定的直说。
                5. 涉及时间与进度的问题，直接用上面给出的今天日期自己推算，不要反问用户今天是几号。

                ## 知识点跳转（重要）
                当你的回答涉及下面清单里的知识点时，在该段末尾单独一行写上：
                [[kp:知识点ID|知识点标题]]
                一次回答最多给 3 个，只标与当前回答最相关的。
                只能使用清单中出现的 ID，严禁自行编造。清单里没有对应知识点时就不要写标记。

                ## 本站知识点清单（ID|标题）
                """);

        sb.append(knowledgeCatalog());

        if (context != null && !context.isBlank()) {
            sb.append("\n\n## 这位用户的学习情况\n");
            sb.append("以下是该用户在站内的真实作答数据，回答学习规划类问题时要结合它，");
            sb.append("但不要生硬复述数字，也不要每句话都提。\n\n");
            sb.append(context);
        }

        return sb.toString();
    }

    /** 中文星期，让模型能判断工作日/周末，安排计划时更自然。 */
    private static String weekdayOf(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private String knowledgeCatalog() {
        String cached = knowledgeCatalog;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (knowledgeCatalog != null) {
                return knowledgeCatalog;
            }
            List<KnowledgePoint> points = pointMapper.selectList(
                    new QueryWrapper<KnowledgePoint>().orderByAsc("point_id"));

            StringBuilder sb = new StringBuilder(points.size() * 40);
            for (KnowledgePoint p : points) {
                sb.append(p.getPointId()).append('|').append(p.getTitle()).append('\n');
            }
            knowledgeCatalog = sb.toString();
            return knowledgeCatalog;
        }
    }

    /** 知识点有变动时（内容重灌）调用，让下次请求重新读库。 */
    public void invalidate() {
        knowledgeCatalog = null;
    }
}