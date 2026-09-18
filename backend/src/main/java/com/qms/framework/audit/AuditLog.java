package com.qms.framework.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。标注在 Service 方法上，由 AuditLogAspect 异步记录到 sys_audit_log。
 * 修改前快照请在业务方法内通过 AuditContext.putBefore(...) 设置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 模块，如「品类管理」 */
    String module();

    /** 动作：CREATE/UPDATE/DELETE_LOGIC/EXPORT/APPROVE 等 */
    String action();

    /** 业务类型英文标识，如 qc_category */
    String bizType() default "";

    /** 业务主键 SpEL，如 "#request.id" / "#id" */
    String bizIdExpr() default "";
}
