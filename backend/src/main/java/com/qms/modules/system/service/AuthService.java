package com.qms.modules.system.service;

import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.framework.audit.LoginLogService;
import com.qms.framework.security.JwtTokenProvider;
import com.qms.framework.security.LoginUser;
import com.qms.framework.security.TokenBlacklistService;
import com.qms.modules.system.dto.LoginRequest;
import com.qms.modules.system.dto.RefreshTokenRequest;
import com.qms.modules.system.entity.SysUser;
import com.qms.modules.system.mapper.SysUserMapper;
import com.qms.modules.system.vo.LoginResponse;
import com.qms.modules.system.vo.UserInfoVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper sysUserMapper;
    private final LoginLogService loginLogService;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = resolveIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();

            var access = tokenProvider.issueAccess(loginUser.getUserId(), loginUser.getUsername(),
                    new ArrayList<>(loginUser.getRoles()));
            var refresh = tokenProvider.issueRefresh(loginUser.getUserId(), loginUser.getUsername());

            SysUser update = new SysUser();
            update.setId(loginUser.getUserId());
            update.setLastLoginAt(LocalDateTime.now());
            sysUserMapper.updateById(update);

            loginLogService.record(request.getUsername(), "LOCAL", true, ip, ua, null);

            return LoginResponse.builder()
                    .accessToken(access.token())
                    .refreshToken(refresh.token())
                    .tokenType("Bearer")
                    .expiresIn(tokenProvider.getAccessTtlSeconds())
                    .username(loginUser.getUsername())
                    .realName(loginUser.getUser().getRealName())
                    .build();
        } catch (BadCredentialsException e) {
            loginLogService.record(request.getUsername(), "LOCAL", false, ip, ua, "用户名或密码错误");
            throw new BizException(ResultCode.AUTH_BAD_CREDENTIAL);
        } catch (DisabledException e) {
            loginLogService.record(request.getUsername(), "LOCAL", false, ip, ua, "账号已停用");
            throw new BizException(ResultCode.AUTH_USER_DISABLED);
        }
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = tokenProvider.parse(request.getRefreshToken());
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new BizException(ResultCode.AUTH_TOKEN_INVALID);
            }
            if (blacklistService.isBlacklisted(claims.getId())) {
                throw new BizException(ResultCode.AUTH_UNAUTHORIZED);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.AUTH_TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.AUTH_USER_DISABLED);
        }
        // 刷新令牌轮换：旧 refresh 立即失效
        blacklistService.blacklist(claims.getId(), claims.getExpiration().toInstant());

        var access = tokenProvider.issueAccess(userId, user.getUsername(), new ArrayList<>());
        var refresh = tokenProvider.issueRefresh(userId, user.getUsername());
        return LoginResponse.builder()
                .accessToken(access.token())
                .refreshToken(refresh.token())
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTtlSeconds())
                .username(user.getUsername())
                .realName(user.getRealName())
                .build();
    }

    public void logout(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = tokenProvider.parse(header.substring(7));
                blacklistService.blacklist(claims.getId(), claims.getExpiration().toInstant());
            } catch (Exception ignored) {
                // 令牌本身已无效，登出幂等成功
            }
        }
    }

    public void verifyPassword(LoginUser loginUser, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, loginUser.getPassword())) {
            throw new BizException(ResultCode.AUTH_PASSWORD_CONFIRM_FAIL);
        }
    }

    public UserInfoVO currentUserInfo(LoginUser loginUser) {
        SysUser user = loginUser.getUser();
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(loginUser.getUserId());
        vo.setUsername(loginUser.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptName());
        vo.setPhone(user.getPhone());
        vo.setRoles(new ArrayList<>(loginUser.getRoles()));
        vo.setPermissions(new ArrayList<>(loginUser.getPermissions()));
        vo.setDataScope(user.getDataScope());
        return vo;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
    }
}
