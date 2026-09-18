package com.qms.framework.audit;

/**
 * 审计上下文：业务方法执行前写入「修改前快照」，切面在方法成功后读取并清理。
 */
public final class AuditContext {

    private static final ThreadLocal<String> BEFORE_HOLDER = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void putBefore(String json) {
        BEFORE_HOLDER.set(json);
    }

    public static String getBefore() {
        return BEFORE_HOLDER.get();
    }

    public static void clear() {
        BEFORE_HOLDER.remove();
    }
}
