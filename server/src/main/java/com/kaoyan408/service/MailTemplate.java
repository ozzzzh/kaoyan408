package com.kaoyan408.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 注册验证码邮件的 HTML 模板。
 *
 * <p>为什么不用模板引擎（Thymeleaf / FreeMarker）：全项目只有这一封邮件，
 * 为它引入一个引擎并多一套语法，收益抵不上维护成本。纯字符串拼接更直白。
 *
 * <p>为什么排版全是 table + 行内样式：
 * <ul>
 *   <li>Outlook（Word 渲染引擎）不认 flex / grid，只认 table；</li>
 *   <li>Gmail 会剥掉 &lt;style&gt; 块和 class，行内 style 才留得住；</li>
 *   <li>不用 &lt;img&gt; 做装饰：多数客户端默认不加载图片，加载前是一堆空白框。</li>
 * </ul>
 *
 * <p>验证码用「每位一个方块」呈现，而不是一整串数字：
 * 反馈里最常出现的不是看不清，而是抄错位，分开摆能减少串行。
 */
final class MailTemplate {

    /** 品牌色。与前端 main.css 里的变量一致：深藏青、青绿、纸白。 */
    private static final String INK = "#1F2440";
    private static final String TEAL = "#00B5A8";
    /** 按钮底色用压暗一档的青绿，白字对比度约 5:1，正文明文才看得清。 */
    private static final String TEAL_DEEP = "#0B7C73";
    private static final String BG = "#F4F7F8";
    private static final String CARD = "#FFFFFF";
    private static final String BORDER = "#E0E8EA";
    private static final String TEXT = "#16202E";
    private static final String MUTED = "#5A6B74";
    private static final String FAINT = "#94A3AA";
    private static final String CODE_BG = "#E4F5F3";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private MailTemplate() {
    }

    /**
     * 渲染正文。
     *
     * @param code      6 位验证码
     * @param minutes   有效期（分钟）
     * @param brand     平台名，如「题知链 408」
     * @param tagline   标语，可为空
     * @param sentAt    发送时间，用于正文里的时间戳
     */
    static String render(String code, int minutes, String brand, String tagline, LocalDateTime sentAt) {
        StringBuilder digits = new StringBuilder();
        for (char c : code.toCharArray()) {
            digits.append(
                    "<td style=\"padding:0 4px;\">"
                            + "<div style=\"width:44px;height:56px;line-height:56px;text-align:center;"
                            + "background:" + CODE_BG + ";border-radius:8px;"
                            + "font-family:Consolas,'SF Mono',Menlo,monospace;"
                            + "font-size:26px;font-weight:700;color:" + INK + ";\">")
                    .append(c)
                    .append("</div></td>");
        }

        String taglineRow = (tagline == null || tagline.isBlank())
                ? ""
                : "<div style=\"margin-top:6px;font-size:12px;color:" + MUTED + ";letter-spacing:0.02em;\">"
                        + escape(tagline) + "</div>";

        return "<!DOCTYPE html>"
                + "<html lang=\"zh-CN\"><head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(brand) + " · 注册验证码</title>"
                + "</head>"
                + "<body style=\"margin:0;padding:0;background:" + BG + ";\">"

                // 外层容器：在 Outlook 里靠 table 居中，在手机上靠 max-width 收窄
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:" + BG + ";padding:32px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:520px;background:" + CARD + ";border:1px solid " + BORDER + ";"
                + "border-radius:14px;overflow:hidden;"
                + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',"
                + "'Hiragino Sans GB','Microsoft YaHei',sans-serif;\">"

                // 顶部品牌条
                + "<tr><td style=\"padding:22px 28px 0 28px;\">"
                + "<div style=\"font-size:17px;font-weight:700;color:" + INK + ";letter-spacing:0.01em;\">"
                + escape(brand) + "</div>"
                + taglineRow
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px 0 28px;\">"
                + "<div style=\"height:2px;background:" + TEAL + ";border-radius:2px;\"></div>"
                + "</td></tr>"

                // 正文
                + "<tr><td style=\"padding:26px 28px 0 28px;\">"
                + "<div style=\"font-size:15px;color:" + TEXT + ";line-height:1.7;\">"
                + "你正在注册 " + escape(brand) + "，验证码是：</div>"
                + "</td></tr>"

                // 验证码：逐位方块
                + "<tr><td align=\"center\" style=\"padding:22px 28px 0 28px;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + digits
                + "</tr></table>"
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:12px 28px 0 28px;\">"
                + "<div style=\"font-size:12px;color:" + MUTED + ";\">"
                + minutes + " 分钟内有效 · 仅可使用一次</div>"
                + "</td></tr>"

                // 提示区
                + "<tr><td style=\"padding:24px 28px 0 28px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td style=\"background:" + CODE_BG + ";border-radius:10px;padding:14px 16px;\">"
                + "<div style=\"font-size:12.5px;color:" + MUTED + ";line-height:1.7;\">"
                + "若不是你本人操作，可忽略本邮件，账号不会被创建。<br>"
                + "请勿把验证码告诉任何人，我们不会向你索要。"
                + "</div></td></tr></table>"
                + "</td></tr>"

                + "<tr><td style=\"padding:26px 28px 0 28px;\">"
                + "<div style=\"font-size:13px;color:" + TEXT + ";\">"
                + "<a href=\"#\" style=\"color:" + TEAL_DEEP + ";text-decoration:none;font-weight:600;\">"
                + escape(brand) + "</a> · 计算机统考备考平台"
                + "</div>"
                + "</td></tr>"

                // 页脚
                + "<tr><td style=\"padding:20px 28px 26px 28px;\">"
                + "<div style=\"border-top:1px solid " + BORDER + ";padding-top:14px;"
                + "font-size:11.5px;color:" + FAINT + ";line-height:1.7;\">"
                + "此邮件由系统自动发送，请勿直接回复。<br>"
                + "发送时间 " + sentAt.format(STAMP)
                + "</div>"
                + "</td></tr>"

                + "</table></td></tr></table></body></html>";
    }

    /** 纯文本兜底：部分客户端或用户偏好纯文本时用它，保证验证码一定拿得到。 */
    static String renderPlain(String code, int minutes, String brand, String sentAt) {
        return "你正在注册 " + brand + "，验证码是：\n\n"
                + "    " + code + "\n\n"
                + minutes + " 分钟内有效，仅可使用一次。\n"
                + "若不是你本人操作，可忽略本邮件，账号不会被创建。\n"
                + "请勿把验证码告诉任何人。\n\n"
                + "—— " + brand + " · 计算机统考备考平台\n"
                + "此邮件由系统自动发送，请勿直接回复。\n"
                + "发送时间 " + sentAt;
    }

    /** 极简转义：正文里只有品牌名来自配置，仍按规矩处理，避免配置里带尖括号把结构撑坏。 */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
