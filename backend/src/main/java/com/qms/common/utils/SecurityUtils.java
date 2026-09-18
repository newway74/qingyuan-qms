package com.qms.common.utils;

import com.qms.common.constant.SecurityConstants;
import com.qms.common.exception.BizException;
import com.qms.common.result.ResultCode;
import com.qms.framework.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 登录上下文工具
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BizException(ResultCode.AUTH_UNAUTHORIZED);
    }

    /** 未登录场景（异步线程/定时任务）返回 null */
    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long getCurrentUserId() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? null : loginUser.getUserId();
    }

    public static String getCurrentUsername() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? "system" : loginUser.getUsername();
    }

    public static Long getTenantId() {
        return SecurityConstants.DEFAULT_TENANT_ID;
    }
}
