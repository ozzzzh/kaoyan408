package com.kaoyan408.service.ai;

/**
 * 图解 DSL 的提示词。
 *
 * <p>为什么不让模型直接写 SVG 或 manim 代码：
 * SVG 的 xmlns、坐标系、字体容易写错；manim-web 的 API 在训练数据里几乎不存在。
 * 这里改成让模型填一份**受限的图形描述**（节点 + 边 + 标注），
 * 由前端负责画成 SVG。模型只需要摆位置和写文字，出错面小得多。
 *
 * <p>格式用 JSON 而不是自然语言，因为要能校验：前端拿到后先过一遍结构检查，
 * 不合格就丢弃，绝不把模型输出当代码执行。
 */
public final class DiagramPrompt {

    private DiagramPrompt() {
    }

    /**
     * 系统提示。
     *
     * @param knowledgeContext 当前问题涉及的知识点标题，帮助模型判断图该画什么
     */
    public static String systemPrompt(String knowledgeContext) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("""
                你是 408（计算机统考）教学图解的绘制助手。
                你的任务是把一道题的原理画成一张**结构图**，供考生理解。

                ## 输出格式（严格遵守）
                只输出一个 JSON 对象，不要任何解释文字，不要 markdown 代码块围栏。

                {
                  "title": "图的标题，不超过 16 字",
                  "width": 720,
                  "height": 420,
                  "nodes": [
                    { "id": "a", "label": "节点文字", "shape": "box", "x": 120, "y": 80, "color": "default" }
                  ],
                  "edges": [
                    { "from": "a", "to": "b", "label": "边上文字", "style": "solid" }
                  ],
                  "notes": [
                    { "text": "图下方的补充说明，可省略", "x": 40, "y": 380 }
                  ]
                }

                ## 字段约束
                - width/height：取 720×420 或 720×480，不要超过 800×600。
                - nodes 最多 12 个，edges 最多 16 条，notes 最多 2 条。
                - node.id：小写字母加数字，全局唯一。
                - node.label：不超过 12 个汉字，可以用 \\n 换行。
                - node.shape：box（矩形，适合状态/对象）、circle（圆，适合节点/顶点）、
                  diamond（菱形，适合判断）、text（纯文字，无边框）。
                - node.x / node.y：**节点中心点**坐标，必须落在画布内并留出边距
                  （x 在 60~width-60，y 在 50~height-70）。
                - node.color：default / accent / warn / danger / muted，最多用两种。
                - edge.style：solid（实线）、dashed（虚线）、arrow（带箭头，默认）。
                - edge.label：不超过 8 个字，可为空字符串。

                ## 排版要求
                - 节点不要重叠，横向间距至少 120，纵向至少 90。
                - 流程图按从左到右或从上到下排，不要绕来绕去。
                - 一张图只讲清一件事，画不下就省略细节，不要塞满。

                ## 内容要求
                - 用图中的节点和连线表达关系与流程，不要把整段文字塞进节点。
                - 图必须与用户问的知识点直接相关，不要画泛泛的示意图。
                """);

        if (knowledgeContext != null && !knowledgeContext.isBlank()) {
            sb.append("\n## 当前知识点\n").append(knowledgeContext).append('\n');
        }
        return sb.toString();
    }

    /** 让模型画图的提问。 */
    public static String userPrompt(String question, String answerExcerpt) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("请为下面的问题画一张图解。\n\n问题：").append(question).append('\n');
        if (answerExcerpt != null && !answerExcerpt.isBlank()) {
            sb.append("\n相关说明（供你判断该画什么，不要把这段整段塞进图里）：\n")
                    .append(answerExcerpt).append('\n');
        }
        sb.append("\n只输出 JSON。");
        return sb.toString();
    }
}