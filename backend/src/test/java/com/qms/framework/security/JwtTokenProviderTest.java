package com.qms.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JWT 签发/解析：claims、类型、过期、篡改、签发方校验、密钥长度、生产密钥缺失 fail-fast。
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    /** 构造一个激活指定 profile 的环境（空数组表示未激活任何 profile，即本地开发场景） */
    private JwtTokenProvider provider(String secret, long accessTtl, long refreshTtl, String issuer, String... activeProfiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        JwtTokenProvider p = new JwtTokenProvider(env);
        ReflectionTestUtils.setField(p, "secret", secret);
        ReflectionTestUtils.setField(p, "accessTtlSeconds", accessTtl);
        ReflectionTestUtils.setField(p, "refreshTtlSeconds", refreshTtl);
        ReflectionTestUtils.setField(p, "issuer", issuer);
        p.init();
        return p;
    }

    @BeforeEach
    void setUp() {
        provider = provider("unit-test-secret-key-must-be-at-least-32-bytes!!", 7200, 604800, "qingyuan-qms");
    }

    @Test
    void issue_and_parse_access_token() {
        JwtTokenProvider.IssuedToken issued = provider.issueAccess(1001L, "inspector", List.of("INSPECTOR", "REVIEWER"));
        assertEquals("access", issued.type());
        assertTrue(issued.expiresAt().isAfter(issued.issuedAt()));

        Claims claims = provider.parse(issued.token());
        assertEquals("1001", claims.getSubject());
        assertEquals("inspector", claims.get("username", String.class));
        assertEquals("access", claims.get("type", String.class));
        assertEquals("qingyuan-qms", claims.getIssuer());
        assertEquals(List.of("INSPECTOR", "REVIEWER"), claims.get("roles", List.class));
    }

    @Test
    void refresh_token_carries_empty_roles() {
        JwtTokenProvider.IssuedToken refresh = provider.issueRefresh(1001L, "inspector");
        assertEquals("refresh", refresh.type());
        Claims claims = provider.parse(refresh.token());
        assertEquals(List.of(), claims.get("roles", List.class));
    }

    @Test
    void expired_token_is_rejected() {
        JwtTokenProvider expired = provider("unit-test-secret-key-must-be-at-least-32-bytes!!", -10, 604800, "qingyuan-qms");
        String token = expired.issueAccess(1L, "u", List.of()).token();
        assertThrows(JwtException.class, () -> provider.parse(token));
    }

    @Test
    void tampered_token_is_rejected() {
        String token = provider.issueAccess(1L, "u", List.of()).token() + "tamper";
        assertThrows(JwtException.class, () -> provider.parse(token));
    }

    @Test
    void token_from_other_issuer_is_rejected() {
        JwtTokenProvider other = provider("unit-test-secret-key-must-be-at-least-32-bytes!!", 7200, 604800, "evil-issuer");
        String token = other.issueAccess(1L, "u", List.of()).token();
        assertThrows(JwtException.class, () -> provider.parse(token));
    }

    @Test
    void short_secret_fails_fast_at_init() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider("short", 7200, 604800, "qingyuan-qms"));
        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    @DisplayName("prod 环境未配置 QMS_JWT_SECRET 时拒绝启动，不允许使用兜底密钥")
    void missing_secret_in_prod_fails_fast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider("", 7200, 604800, "qingyuan-qms", "prod"));
        assertTrue(ex.getMessage().contains("QMS_JWT_SECRET"));
    }

    @Test
    @DisplayName("本地开发环境未配置密钥时使用兜底密钥，令牌可正常签发与解析")
    void missing_secret_outside_prod_falls_back() {
        JwtTokenProvider fallback = provider("", 7200, 604800, "qingyuan-qms");
        String token = fallback.issueAccess(1L, "dev", List.of("ADMIN")).token();
        Claims claims = fallback.parse(token);
        assertEquals("dev", claims.get("username", String.class));
    }

    @Test
    void ttl_accessor() {
        assertEquals(7200L, provider.getAccessTtlSeconds());
    }
}
