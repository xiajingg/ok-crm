package com.okcrm.platform.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.okcrm.platform.common.security.CurrentUserProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充：创建人/时间、更新人/时间、逻辑删除标记。
 *
 * <p>业务代码不需要也不应该手动给这些字段赋值。</p>
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final ObjectProvider<CurrentUserProvider> currentUserProvider;

    public AuditMetaObjectHandler(ObjectProvider<CurrentUserProvider> currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);

        Long userId = currentUserId();
        if (userId != null) {
            strictInsertFill(metaObject, "createdBy", Long.class, userId);
            strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        }
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());

        Long userId = currentUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }

    private Long currentUserId() {
        CurrentUserProvider provider = currentUserProvider.getIfAvailable();
        return provider == null ? null : provider.currentUserId();
    }
}
