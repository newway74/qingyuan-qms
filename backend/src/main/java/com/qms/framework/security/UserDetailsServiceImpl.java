package com.qms.framework.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = sysUserMapper.selectPermCodesByUserId(user.getId());
        String scope = sysUserMapper.selectDataScopeByUserId(user.getId());

        user.setRoles(roles);
        user.setPermissions(perms);
        user.setDataScope(scope == null ? "ALL" : scope);
        return new LoginUser(user, new HashSet<>(roles), new HashSet<>(perms));
    }
}
