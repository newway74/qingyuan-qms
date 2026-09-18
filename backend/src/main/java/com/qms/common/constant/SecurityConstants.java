package com.qms.common.constant;

/**
 * 系统级常量
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** Redis key：令牌黑名单 jti */
    public static final String CACHE_TOKEN_BLACKLIST = "qms:token:blacklist:";
    /** Redis key：登录失败次数 */
    public static final String CACHE_LOGIN_FAIL = "qms:login:fail:";
    /** Redis key：幂等 */
    public static final String CACHE_IDEMPOTENT = "qms:idempotent:";
    /** Redis key：单据日序号 */
    public static final String CACHE_DOC_SEQ = "qms:seq:doc:";

    public static final long DEFAULT_TENANT_ID = 1L;

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SAMPLER = "SAMPLER";
    public static final String ROLE_INSPECTOR = "INSPECTOR";
    public static final String ROLE_REVIEWER = "REVIEWER";
    public static final String ROLE_QA_MANAGER = "QA_MANAGER";

    /** 管理员通配权限 */
    public static final String PERM_ALL = "*";
}
