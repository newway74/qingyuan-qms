package com.qms.framework.security;

import com.qms.modules.system.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Spring Security 当前登录用户主体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private SysUser user;

    /** 角色 code 集合 */
    private Set<String> roles;

    /** 权限码集合（ADMIN 含 *） */
    private Set<String> permissions;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }

    @Override
    public String getPassword() {
        return user == null ? null : user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user == null ? null : user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user != null && user.getStatus() != null && user.getStatus() == 1;
    }

    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }

    public boolean hasPerm(String perm) {
        if (permissions == null) {
            return false;
        }
        if (permissions.contains("*") || permissions.contains(perm)) {
            return true;
        }
        // 支持 master:category:* 这类通配
        return permissions.stream().anyMatch(p -> p.endsWith(":*")
                && perm.startsWith(p.substring(0, p.length() - 1)));
    }
}
