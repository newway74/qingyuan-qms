package com.qms.framework.security;

import com.qms.common.utils.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * 权限校验 Bean，供 @PreAuthorize("@perm.has('xxx')") 使用，支持 * 与模块:* 通配。
 */
@Component("perm")
public class PermissionChecker {

    public boolean has(String permCode) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        return loginUser != null && loginUser.hasPerm(permCode);
    }

    public boolean hasAny(String... permCodes) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        for (String code : permCodes) {
            if (loginUser.hasPerm(code)) {
                return true;
            }
        }
        return false;
    }
}
