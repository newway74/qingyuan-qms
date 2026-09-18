package com.qms.framework.datascope;

import com.qms.modules.system.entity.SysUser;
import com.qms.framework.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据权限上下文：未登录默认 ALL、登录用户按 sys_user.data_scope。
 */
class DataScopeContextTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(SysUser user, Set<String> perms) {
        LoginUser loginUser = new LoginUser(user, Set.of("INSPECTOR"), perms);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @Test
    void anonymous_defaults_to_all_scope() {
        assertEquals("ALL", DataScopeContext.currentScope());
        assertTrue(DataScopeContext.isAllScope());
    }

    @Test
    void login_user_without_user_entity_defaults_to_all() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(), null));
        assertEquals("ALL", DataScopeContext.currentScope());
    }

    @Test
    void category_scoped_user_is_not_all() {
        SysUser user = new SysUser();
        user.setDataScope("CATEGORY");
        loginAs(user, Set.of());

        assertEquals("CATEGORY", DataScopeContext.currentScope());
        assertFalse(DataScopeContext.isAllScope());
    }
}
