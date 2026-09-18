package com.qms.framework.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等：请求头携带 Idempotency-Key，相同 Key 在 TTL 内只执行一次，重复请求返回首次结果。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 有效期（秒），默认 24 小时 */
    long ttl() default 86400L;
}
