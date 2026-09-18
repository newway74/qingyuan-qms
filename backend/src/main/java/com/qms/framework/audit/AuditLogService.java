package com.qms.framework.audit;

import com.qms.modules.system.entity.SysAuditLog;
import com.qms.modules.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志落库。只增不删，应用层无任何修改/删除入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    @Async("auditExecutor")
    public void saveAsync(SysAuditLog auditLog) {
        try {
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计失败不影响主交易，但必须有错误痕迹
            log.error("审计日志落库失败 module={}, action={}, bizId={}",
                    auditLog.getModule(), auditLog.getAction(), auditLog.getBizId(), e);
        }
    }
}
