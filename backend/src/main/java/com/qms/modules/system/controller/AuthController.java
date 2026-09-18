package com.qms.modules.system.controller;

import com.qms.common.result.R;
import com.qms.common.utils.SecurityUtils;
import com.qms.framework.security.LoginUser;
import com.qms.integration.sso.SsoAdapter;
import com.qms.modules.system.dto.LoginRequest;
import com.qms.modules.system.dto.RefreshTokenRequest;
import com.qms.modules.system.dto.VerifyPasswordRequest;
import com.qms.modules.system.service.AuthService;
import com.qms.modules.system.vo.LoginResponse;
import com.qms.modules.system.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SsoAdapter ssoAdapter;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, httpRequest));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return R.ok();
    }

    @Operation(summary = "当前登录用户信息（角色/权限码/数据范围）")
    @GetMapping("/userinfo")
    public R<UserInfoVO> userInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return R.ok(authService.currentUserInfo(loginUser));
    }

    @Operation(summary = "敏感操作二次密码认证（电子签名前置）")
    @PostMapping("/verify-password")
    public R<Void> verifyPassword(@Valid @RequestBody VerifyPasswordRequest request) {
        authService.verifyPassword(SecurityUtils.getLoginUser(), request.getPassword());
        return R.ok();
    }

    @Operation(summary = "SSO 入口（占位，当前为本地适配）")
    @PostMapping("/sso/entry")
    public R<Void> ssoEntry(@RequestParam String ticket) {
        ssoAdapter.exchange(ticket);
        return R.ok();
    }
}
