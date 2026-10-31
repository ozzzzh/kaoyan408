package com.kaoyan408.service.ai;

/**
 * 动画 DSL 的提示词。
 *
 * <p>和静态图解的区别：多了 steps，每一步描述「这一帧发生什么」。
 * 关键设计是**每步只描述增量**（谁高亮、谁移动、加哪条边、配一句旁白），
 * 而不是每步重画整张图——后者模型要重复输出全部节点，既费 token 又容易前后不一致。
 *
 * <p>为什么不用 manim-web：那套 API 在模型训练数据里几乎不存在，
 * 让模型写调用代码基本跑不起来。这里让模型只填「这一步谁动、动到哪」，
 * 由前端做状态切换与过渡。
 */
public final class AnimationPrompt {

    private AnimationPrompt() {
    }

    public static String systemPrompt(String knowledgeContext) {
        StringBuilder sb = new StringBuilder(3072);
        sb.append("""
                你是 408（计算机统考）教学动画的分镜设计助手。
                把一个问题拆成 3~6 个步骤，让学生一步步看懂过程。

                ## 输出格式（严格遵守）
                只输出一个 JSON 对象，不要解释文字，不要 markdown 围栏。

                {
                  "title": "动画标题，不超过 16 字",
                  "width": 720,
                  "height": 420,
                  "nodes": [
                    { "id": "a", "label": "就绪", "shape": "box", "x": 160, "y": 120, "color": "default" }
                  ],
                  "edges": [
                    { "from": "a", "to": "b", "label": "调度", "style": "arrow" }
                  ],
                  "steps": [
                    {
                      "caption": "第一步的旁白，一句话，不超过 30 字",
                      "highlight": ["a"],
                      "dim": ["b"],
                      "addEdges": [{ "from": "a", "to": "b", "label": "调度", "style": "arrow" }],
                      "moveNodes": [{ "id": "a", "x": 320, "y": 200 }]
                    }
                  ]
                }

                ## 字段约束
                - nodes / edges 是**完整**的初始布局，最多 10 个节点、14 条边。
                - steps 为 3~6 步，每一步只能包含下列可选字段：
                  - caption（必填）：这一步在讲什么，一句话
                  - highlight：这一步要点亮的节点 id 数组（会被强调）
                  - dim：这一步要淡化的节点 id 数组（背景信息）
                  - addEdges：这一步新出现的连线（后续步骤会保留）
                  - moveNodes：这一步移动的节点及目标坐标
                - 每步只需写出**这一步变化的部分**，不要重复全部节点。
                - 坐标是节点中心点，x 在 60~width-60，y 在 50~height-70。

                ## 分镜要求
                - 每步只讲一件事。不要在一步里同时做三件事。
                - caption 按「先做什么，再做什么」推进，像老师边画边讲。
                - 第一步通常是初始状态，最后一布给出结论或易错点。
                - 节点在画布内移动时不要重叠。
                - **布局要铺开**：节点纵向分布在 height 的 25%~75% 之间，
                  不要全部挤在顶部或挤成一列；三个节点优先摆成三角形而非一条直线。
                - 如果这个问题不适合分步（比如纯概念定义），steps 给 3 步即可，不要硬凑。
                """);

        if (knowledgeContext != null && !knowledgeContext.isBlank()) {
            sb.append("\n## 当前知识点\n").append(knowledgeContext).append('\n');
        }
        return sb.toString();
    }

    public static String userPrompt(String question, String answerExcerpt) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("请为下面的问题设计一段分步动画。\n\n问题：").append(question).append('\n');
        if (answerExcerpt != null && !answerExcerpt.isBlank()) {
            sb.append("\n相关说明（供你判断该怎么分步，不要把这段塞进 caption）：\n")
                    .append(answerExcerpt).append('\n');
        }
        sb.append("\n只输出 JSON。");
        return sb.toString();
    }
}