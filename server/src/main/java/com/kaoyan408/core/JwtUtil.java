package com.kaoyan408.core;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>token 里带 {@code tokenVersion}，与 app_user.token_version 对齐：
 * 只验签不查库的话，被禁用的账号、改过密码的账号、被删掉的账号，
 * 手里的旧 token 仍然能一直用到过期（默认 7 天）。有了版本号，
 * 服务端只要把用户行的 token_version 加一，旧 token 立刻失效。
 */
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

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-seconds:7200}") long expireSeconds) {
        // 启动即失败，而不是等出事：漏配 JWT_SECRET 时应用能起来、页面也正常，
        // 唯一的症状是「任何人可以伪造任何账号」——这种默认值必须挡在启动阶段。
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，拒绝启动。");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET 仍是仓库里的默认值，任何人都能伪造登录态。"
                            + "请设置一个随机长字符串（至少 32 字节），例如："
                            + "openssl rand -base64 48");
        }
        String trimmed = secret.trim();
        int length = trimmed.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 太短（" + length + " 字节），HS256 至少需要 " + MIN_SECRET_BYTES + " 字节。");
        }
        this.key = Keys.hmacShaKeyFor(trimmed.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
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
