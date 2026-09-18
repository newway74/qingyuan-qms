package com.qms.framework.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解。阶段1仅贯通 ALL 范围（默认全量），
 * CATEGORY/BRAND/DEPT 的 SQL 拼装在阶段2主数据列表落地。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 主表别名（用于拼 SQL 条件），如 p */
    String alias() default "";

    /** 品类字段 */
    String categoryField() default "category_id";

    /** 品牌字段 */
    String brandField() default "brand";

    /** 部门字段 */
    String deptField() default "dept_id";
}
