package com.qms.framework.datascope;

import com.qms.common.utils.SecurityUtils;
import com.qms.framework.security.LoginUser;

/**
 * 数据权限上下文。后续阶段 MyBatis 拦截器据此拼接品类/品牌/部门范围条件。
 */
public final class DataScopeContext {

    private DataScopeContext() {
    }

    public static String currentScope() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null || user.getUser() == null || user.getUser().getDataScope() == null) {
            return "ALL";
        }
        return user.getUser().getDataScope();
    }

    public static boolean isAllScope() {
        return "ALL".equals(currentScope());
    }
}
