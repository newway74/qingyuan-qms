package com.qms.framework.security;

import com.qms.modules.system.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限码校验与 LoginUser：通配符、未登录拒绝、用户状态映射。
 */
class PermissionCheckerTest {

    private final PermissionChecker checker = new PermissionChecker();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(LoginUser loginUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @Nested
    @DisplayName("@perm 权限检查")
    class PermCheck {

        @Test
        void anonymous_has_nothing() {
            assertFalse(checker.has("inspection:task:view"));
            assertFalse(checker.hasAny("inspection:task:view", "dashboard:view"));
        }

        @Test
        void exact_perm_match() {
            login(new LoginUser(new SysUser(), Set.of("INSPECTOR"), Set.of("inspection:task:view")));
            assertTrue(checker.has("inspection:task:view"));
            assertFalse(checker.has("inspection:task:assign"));
        }

        @Test
        void star_perm_matches_everything() {
            login(new LoginUser(new SysUser(), Set.of("ADMIN"), Set.of("*")));
            assertTrue(checker.has("anything:at:all"));
            assertTrue(checker.hasAny("a", "b"));
        }

        @Test
        void module_wildcard_match() {
            login(new LoginUser(new SysUser(), Set.of("QA_MANAGER"), Set.of("master:*")));
            assertTrue(checker.has("master:product:list"));
            assertFalse(checker.has("inspection:task:view"));
            assertTrue(checker.hasAny("inspection:task:view", "master:product:list"));
            assertFalse(checker.hasAny("inspection:task:view", "dashboard:view"));
        }
    }

    @Nested
    @DisplayName("LoginUser 主体")
    class Subject {

        @Test
        void null_permissions_never_match() {
            LoginUser u = new LoginUser(new SysUser(), Set.of("SAMPLER"), null);
            assertFalse(u.hasPerm("x"));
        }

        @Test
        void identity_and_admin() {
            SysUser user = new SysUser();
            user.setId(7L);
            user.setUsername("sampler");
            user.setPasswordHash("hashed-pw");
            user.setStatus(1);
            LoginUser u = new LoginUser(user, Set.of("SAMPLER"), Set.of());
            assertEquals(7L, u.getUserId());
            assertEquals("sampler", u.getUsername());
            assertEquals("hashed-pw", u.getPassword());
            assertTrue(u.isEnabled());
            assertTrue(u.isAccountNonExpired());
            assertTrue(u.isAccountNonLocked());
            assertTrue(u.isCredentialsNonExpired());
            assertFalse(u.isAdmin());

            LoginUser admin = new LoginUser(user, Set.of("ADMIN"), Set.of("*"));
            assertTrue(admin.isAdmin());
            assertTrue(admin.isEnabled());
        }

        @Test
        void disabled_and_empty_user() {
            SysUser disabled = new SysUser();
            disabled.setStatus(0);
            assertFalse(new LoginUser(disabled, Set.of(), Set.of()).isEnabled());

            LoginUser empty = new LoginUser();
            assertNull(empty.getUserId());
            assertNull(empty.getUsername());
            assertNull(empty.getPassword());
            assertFalse(empty.isEnabled());
            assertTrue(empty.getAuthorities().isEmpty());
        }

        @Test
        void roles_become_roled_authorities() {
            LoginUser u = new LoginUser(new SysUser(), Set.of("SAMPLER", "INSPECTOR"), Set.of());
            Set<String> authorities = u.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("ROLE_SAMPLER", "ROLE_INSPECTOR"), authorities);
        }
    }
}
