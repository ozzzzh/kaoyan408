package com.kaoyan408.core;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析。
 *
 * <p>为什么需要单独一个类：原来 AuthController 与 AiController 各自写了一份，
 * 而且都优先取 {@code X-Forwarded-For} 的**第一段**。nginx 用的是
 * {@code proxy_add_x_forwarded_for}，它会把客户端自带的 XFF 原样保留在前面，
 * 于是只要请求里加一个 {@code X-Forwarded-For: 1.2.3.4}，就能换一个限流桶——
 * AI 游客额度（有真实成本）与发信 IP 限制同时失效。
 *
 * <p>现在改为只信 {@code X-Real-IP}：web/nginx.conf 里写的是
 * {@code proxy_set_header X-Real-IP $remote_addr}，这是 nginx **无条件覆盖**的，
 * 客户端传什么都会被冲掉。没有该头时退回 socket 地址。
 *
 * <p>前提：api 容器不直接对外暴露端口（compose 里只有 {@code expose}，没有 ports），
 * 所有流量都经过 nginx。若将来把 api 端口映射到宿主机直连，这个前提就破了，
 * 那时必须改成「按受信代理网段判断，取 XFF 里最后一个非受信跳」。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
