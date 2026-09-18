package com.qms.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.qms.common.constant.SecurityConstants;
import com.qms.common.utils.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充：创建人/创建时间/更新人/更新时间/租户
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityUtils.getCurrentUserId();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        this.strictInsertFill(metaObject, "tenantId", Long.class, SecurityConstants.DEFAULT_TENANT_ID);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
        Integer lockVersion = (Integer) getFieldValByName("lockVersion", metaObject);
        if (lockVersion == null) {
            this.strictInsertFill(metaObject, "lockVersion", Integer.class, 0);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, SecurityUtils.getCurrentUserId());
    }
}
