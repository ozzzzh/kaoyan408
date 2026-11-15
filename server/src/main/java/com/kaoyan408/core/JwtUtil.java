package com.kaoyan408.core;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>token 里带 {@code tokenVersion}，与 app_user.token_version 对齐：
 * 只验签不查库的话，被禁用的账号、改过密码的账号、被删掉的账号，
 * 手里的旧 token 仍然能一直用到过期（默认 7 天）。有了版本号，
 * 服务端只要把用户行的 token_version 加一，旧 token 立刻失效。
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * 仓库里公开的默认密钥。它不是「弱密钥」，而是等于没有密钥：
     * 任何人都能用它签出任意账号的 token，所以只在本地开发时允许出现。
     */
    private static final String DEV_DEFAULT_SECRET = "kaoyan408-local-dev-secret-change-before-launch";

    /** HS256 要求至少 256 位密钥，jjwt 本身也会拒绝更短的。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expireSeconds;

    /**
     * @param secret          签名密钥
     * @param expireSeconds   有效期
     * @param allowInsecure   应急开关：为 true 时即使密钥是公开默认值/过短也只告警不拒启
     *
     * <p>为什么要这个开关：校验做成 fail-closed 能挡住「用公开默认密钥上线」，但代价是
     * 一条配置不对就整个站点 502。折中做法是——默认仍然拒绝启动（安全优先），同时提供
     * 一个显式环境变量作为应急通道：运维发现起不来时，改一个变量重启即可恢复服务，
     * 不需要改代码或重新构建镜像。默认关闭，所以不会悄悄退化。
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-seconds:7200}") long expireSeconds,
                   @Value("${kaoyan.auth.allow-insecure-jwt-secret:false}") boolean allowInsecure) {
        if (secret == null || secret.isBlank()) {
            // 这一条不放开：连密钥都没有时，签发出来的 token 无法校验，等于放行所有人。
            throw new IllegalStateException(
                    "JWT_SECRET 未配置，拒绝启动。请在环境变量或 .env 中设置一个随机长字符串。");
        }
        String trimmed = secret.trim();
        int length = trimmed.getBytes(StandardCharsets.UTF_8).length;
        byte[] keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);

        if (DEV_DEFAULT_SECRET.equals(trimmed)) {
            if (!allowInsecure) {
                throw new IllegalStateException(
                        "JWT_SECRET 仍是仓库里的默认值，任何人都能伪造登录态，因此拒绝启动。"
                                + "请设置随机长字符串，例如：openssl rand -base64 48"
                                + "；若你正在紧急恢复线上服务，可显式设置 "
                                + "KAOYAN_AUTH_ALLOW_INSECURE_JWT_SECRET=true 先让它起来，之后再换掉密钥。");
            }
            log.warn("!!! JWT_SECRET 是仓库里的公开默认值，当前登录态可被任何人伪造。"
                    + "你已通过 KAOYAN_AUTH_ALLOW_INSECURE_JWT_SECRET 显式放行，请尽快更换密钥。");
        } else if (length < MIN_SECRET_BYTES) {
            if (!allowInsecure) {
                throw new IllegalStateException(
                        "JWT_SECRET 太短（" + length + " 字节），HS256 至少需要 " + MIN_SECRET_BYTES + " 字节。"
                                + "应急放行：KAOYAN_AUTH_ALLOW_INSECURE_JWT_SECRET=true（届时会对密钥做 SHA-256 派生）。");
            }
            // 短密钥无法直接用于 HS256，做一次派生让它至少满足长度要求（熵并没有增加）
            keyBytes = sha256(keyBytes);
            log.warn("!!! JWT_SECRET 过短（{} 字节），已按 SHA-256 派生为 32 字节继续启动。"
                    + "熵没有增加，仍属弱密钥，请尽快更换。", length);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expireSeconds = expireSeconds;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
    }

    public String sign(String username, String email, int tokenVersion) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("email", email)
                .claim("ver", tokenVersion)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireSeconds * 1000L))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验签名与有效期；失败抛运行时异常，由调用方决定是否忽略。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String subject(String token) {
        return parse(token).getSubject();
    }

    /** 令牌版本；老 token（升级前签发的）没有这个字段，按 0 处理。 */
    public int tokenVersion(Claims claims) {
        Object raw = claims.get("ver");
        return raw instanceof Number number ? number.intValue() : 0;
    }

    public long expiresAt(String token) {
        return parse(token).getExpiration().getTime();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
