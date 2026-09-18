package com.qms.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 签发与解析（HS256）。access / refresh 双令牌。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /**
     * 仅用于本地开发/单机离线环境的兜底密钥（不对外提供服务、只监听 127.0.0.1）。
     * 生产环境（prod）不允许使用该密钥：未设置 QMS_JWT_SECRET 时直接拒绝启动。
     */
    private static final String DEV_FALLBACK_SECRET = "qms-local-dev-fallback-secret-do-not-use-in-prod-0123456789";

    @Value("${qms.jwt.secret:}")
    private String secret;

    @Value("${qms.jwt.access-token-ttl:7200}")
    private long accessTtlSeconds;

    @Value("${qms.jwt.refresh-token-ttl:604800}")
    private long refreshTtlSeconds;

    @Value("${qms.jwt.issuer:qingyuan-qms}")
    private String issuer;

    private final Environment environment;

    public JwtTokenProvider(Environment environment) {
        this.environment = environment;
    }

    @Getter
    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        boolean prodActive = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (secret == null || secret.isBlank()) {
            if (prodActive) {
                // 生产环境 fail-fast：宁可启动失败，也不使用可预测的默认密钥签发令牌
                throw new IllegalStateException(
                        "生产环境必须通过环境变量 QMS_JWT_SECRET 配置 JWT 签名密钥（随机字符串，长度 >= 32 字节），系统已拒绝启动");
            }
            log.warn("未配置环境变量 QMS_JWT_SECRET，当前使用仅限本地开发/单机离线的内置兜底密钥，请勿在对外提供服务的环境中这样运行");
            secret = DEV_FALLBACK_SECRET;
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("qms.jwt.secret 长度至少需要 32 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedToken issueAccess(Long userId, String username, List<String> roles) {
        return issue(userId, username, roles, "access", Duration.ofSeconds(accessTtlSeconds));
    }

    public IssuedToken issueRefresh(Long userId, String username) {
        return issue(userId, username, List.of(), "refresh", Duration.ofSeconds(refreshTtlSeconds));
    }

    private IssuedToken issue(Long userId, String username, List<String> roles, String type, Duration ttl) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("username", username)
                .claim("type", type)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, jti, now, exp, type);
    }

    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    /**
     * 签发结果
     */
    public record IssuedToken(String token, String jti, Instant issuedAt, Instant expiresAt, String type) {
    }
}
